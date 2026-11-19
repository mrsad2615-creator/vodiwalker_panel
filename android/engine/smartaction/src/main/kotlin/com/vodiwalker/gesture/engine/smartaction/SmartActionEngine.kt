package com.vodiwalker.gesture.engine.smartaction

import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.CircuitBreaker
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.common.Sleeper
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.ActionGuard
import com.vodiwalker.gesture.core.model.ActionNode
import com.vodiwalker.gesture.core.model.AppPermission
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.ExecutionEnvironment
import com.vodiwalker.gesture.core.model.ExecutionReport
import com.vodiwalker.gesture.core.model.HardwareToggleType
import com.vodiwalker.gesture.core.model.NodeExecutionStatus
import com.vodiwalker.gesture.core.model.NodeExecutionTrace
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.TriggerScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Platform execution port implemented by `:service:accessibility` using Android 14/15 APIs
 * (`AccessibilityService.performGlobalAction`, `dispatchGesture`, `CameraManager.setTorchMode`,
 * `AudioManager.dispatchMediaKeyEvent`, `PackageManager.getLaunchIntentForPackage`).
 */
fun interface PlatformActionExecutor {
    /**
     * Runs one command on the calling background thread within [timeoutMillis].
     */
    fun execute(command: ActionCommand, timeoutMillis: Long): AppResult<Unit, GestureError>
}

/**
 * Validates [SmartActionGraph] topology using DFS 3-color cycle detection and bounded depth checks
 * before any graph is persisted to Room or executed by the Accessibility service.
 */
object ActionGraphValidator {
    const val MAX_GRAPH_DEPTH: Int = 24
    const val MAX_GRAPH_NODES: Int = 64

    /**
     * Validates node count, referential integrity, cycles, and depth before execution.
     */
    fun validate(graph: SmartActionGraph): AppResult<Unit, GestureError> {
        if (graph.nodes.isEmpty()) {
            return AppResult.Err(GestureError.InvalidInput("Action graph '${graph.id}' contains no nodes."))
        }
        if (graph.nodes.size > MAX_GRAPH_NODES) {
            return AppResult.Err(
                GestureError.InvalidInput("Action graph exceeds maximum of $MAX_GRAPH_NODES nodes."),
            )
        }
        val byId = graph.nodes.associateBy { it.id }
        if (byId.size != graph.nodes.size) {
            return AppResult.Err(GestureError.InvalidInput("Action graph contains duplicate node IDs."))
        }
        if (graph.entryNodeId !in byId) {
            return AppResult.Err(
                GestureError.MissingNodeReference(fromNodeId = "<entry>", missingTargetId = graph.entryNodeId),
            )
        }

        // Verify all outgoing & fallback references exist
        for (node in graph.nodes) {
            val targets = buildList {
                addAll(node.nextNodeIds)
                node.fallbackNodeId?.let(::add)
            }
            for (targetId in targets) {
                if (targetId !in byId) {
                    return AppResult.Err(GestureError.MissingNodeReference(node.id, targetId))
                }
            }
        }

        // 3-color DFS: 0 = unvisited, 1 = visiting (active stack), 2 = verified acyclic
        val color = HashMap<String, Int>(byId.size)
        val activeStack = ArrayList<String>()
        val maxDepthMemo = HashMap<String, Int>(byId.size)

        /**
         * Depth-first three-colour traversal returning the discovered depth or a cycle error.
         */
        fun dfs(nodeId: String): AppResult<Int, GestureError> {
            color[nodeId] = 1
            activeStack.add(nodeId)
            val node = byId.getValue(nodeId)
            val neighbors = buildList {
                addAll(node.nextNodeIds)
                node.fallbackNodeId?.let(::add)
            }
            var deepestChild = 0
            for (next in neighbors) {
                when (color[next] ?: 0) {
                    1 -> {
                        val cycleStart = activeStack.indexOf(next).coerceAtLeast(0)
                        val cycle = activeStack.subList(cycleStart, activeStack.size) + next
                        return AppResult.Err(GestureError.CyclicActionGraph(cycle))
                    }
                    2 -> deepestChild = maxOf(deepestChild, maxDepthMemo[next] ?: 1)
                    else -> when (val sub = dfs(next)) {
                        is AppResult.Err -> return sub
                        is AppResult.Ok -> deepestChild = maxOf(deepestChild, sub.value)
                    }
                }
            }
            activeStack.removeAt(activeStack.lastIndex)
            color[nodeId] = 2
            val myDepth = 1 + deepestChild
            maxDepthMemo[nodeId] = myDepth
            return if (myDepth > MAX_GRAPH_DEPTH) {
                AppResult.Err(GestureError.DepthLimitExceeded(myDepth, MAX_GRAPH_DEPTH))
            } else {
                AppResult.Ok(myDepth)
            }
        }

        return when (val res = dfs(graph.entryNodeId)) {
            is AppResult.Err -> res
            is AppResult.Ok -> AppResult.Ok(Unit)
        }
    }

    /** Returns the exact set of [AppPermission]s required to execute all nodes in [graph]. */
    fun requiredPermissions(graph: SmartActionGraph): Set<AppPermission> {
        val perms = LinkedHashSet<AppPermission>()
        for (node in graph.nodes) {
            requiredPermissionFor(node.command)?.let(perms::add)
        }
        return perms
    }

    /**
     * Maps a command to the platform permission it needs, or null when unrestricted.
     */
    fun requiredPermissionFor(command: ActionCommand): AppPermission? = when (command) {
        is ActionCommand.GlobalSystemAction,
        is ActionCommand.SynthesizeGesture,
        is ActionCommand.SemanticNodeAction,
        -> AppPermission.ACCESSIBILITY_SERVICE

        is ActionCommand.ToggleHardware -> when (command.toggle) {
            HardwareToggleType.TORCH_FLASHLIGHT -> AppPermission.CAMERA_TORCH_CONTROL
            HardwareToggleType.AUTO_ROTATE_DISPLAY -> AppPermission.WRITE_SYSTEM_SETTINGS
            HardwareToggleType.ONE_HANDED_OVERLAY -> AppPermission.ACCESSIBILITY_SERVICE
        }

        is ActionCommand.LaunchApplication,
        is ActionCommand.OpenDeepLink,
        is ActionCommand.MediaTransport,
        is ActionCommand.EmitHaptic,
        is ActionCommand.WaitDelay,
        -> null
    }
}

/**
 * Resolves the single winning [AppTriggerRule] for a recognized gesture using deterministic
 * specificity precedence: `Activity (400) > Package (300) > Category (200) > Global (100)`,
 * broken by user-defined `priority`, while enforcing per-rule cooldown windows.
 */
class AppTriggerResolver(
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
) {
    private val lastFiredMillis = ConcurrentHashMap<String, Long>()

    /**
     * Picks the most specific, enabled, non-cooling rule for [gestureId].
     */
    fun resolve(
        gestureId: String,
        rules: List<AppTriggerRule>,
        environment: ExecutionEnvironment,
    ): AppTriggerRule? {
        if (environment.isPrivacySensitiveWindow) return null
        val now = clock.nowMillis()

        val winning = rules
            .asSequence()
            .filter { it.isEnabled && it.gestureId == gestureId }
            .mapNotNull { rule ->
                val spec = specificityScore(rule.scope, environment) ?: return@mapNotNull null
                rule to (spec * 10_000 + rule.priority.coerceIn(-999, 9_999))
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .firstOrNull { rule ->
                val prev = lastFiredMillis[rule.id]
                prev == null || (now - prev) >= rule.cooldownMillis
            }

        if (winning != null) {
            lastFiredMillis[winning.id] = now
        }
        return winning
    }

    private fun specificityScore(scope: TriggerScope, env: ExecutionEnvironment): Int? = when (scope) {
        is TriggerScope.Activity -> if (
            env.foregroundPackage == scope.packageName &&
            env.foregroundActivity == scope.activityClassName
        ) {
            400
        } else {
            null
        }
        is TriggerScope.Package -> if (env.foregroundPackage == scope.packageName) 300 else null
        is TriggerScope.Category -> if (env.appCategory == scope.category) 200 else null
        TriggerScope.Global -> 100
    }
}

/**
 * Engine #3: Smart-Action DAG Execution Engine.
 *
 * Guarantees zero-crash automation via preflight DAG validation, permission checks, per-node
 * circuit breakers, retry isolation, fallback edges, and synthetic gesture loop protection.
 */
class SmartActionEngine(
    private val executor: PlatformActionExecutor,
    private val syntheticGuard: SyntheticGestureGuard = SyntheticGestureGuard(),
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
    private val sleeper: Sleeper = Sleeper.BLOCKING,
    private val breakerFailureThreshold: Int = 3,
    private val breakerResetMillis: Long = 8_000L,
) {
    private val nodeBreakers = ConcurrentHashMap<String, CircuitBreaker>()

    /**
     * Executes a validated graph and returns a complete, privacy-safe [ExecutionReport].
     */
    fun executeGraph(
        graph: SmartActionGraph,
        environment: ExecutionEnvironment,
        triggerRuleId: String? = null,
    ): ExecutionReport {
        val startNanos = clock.nowNanos()

        if (environment.isPrivacySensitiveWindow) {
            return ExecutionReport(
                graphId = graph.id,
                triggerRuleId = triggerRuleId,
                overallSuccess = false,
                traces = listOf(
                    NodeExecutionTrace(
                        nodeId = graph.entryNodeId,
                        status = NodeExecutionStatus.SKIPPED_BY_GUARD,
                        attempts = 0,
                        durationMicros = 0L,
                        detail = "Blocked by Android 14+ sensitive-window privacy shield.",
                    ),
                ),
                totalDurationMicros = elapsedMicros(startNanos),
                syntheticGestureToken = null,
            )
        }

        val validation = ActionGraphValidator.validate(graph)
        if (validation is AppResult.Err) {
            return ExecutionReport(
                graphId = graph.id,
                triggerRuleId = triggerRuleId,
                overallSuccess = false,
                traces = listOf(
                    NodeExecutionTrace(
                        nodeId = graph.entryNodeId,
                        status = NodeExecutionStatus.FAILED_TERMINAL,
                        attempts = 0,
                        durationMicros = elapsedMicros(startNanos),
                        detail = "Graph validation failed: ${validation.error}",
                    ),
                ),
                totalDurationMicros = elapsedMicros(startNanos),
                syntheticGestureToken = null,
            )
        }

        val nodeMap = graph.nodes.associateBy { it.id }
        val queue = ArrayDeque<String>().apply { addLast(graph.entryNodeId) }
        val visited = HashSet<String>()
        val traces = ArrayList<NodeExecutionTrace>()
        var syntheticToken: Long? = null
        var terminalFailure = false
        val deadlineMillis = clock.nowMillis() + graph.maxExecutionTimeMillis.coerceAtLeast(100L)

        while (queue.isNotEmpty()) {
            if (clock.nowMillis() > deadlineMillis) {
                terminalFailure = true
                traces.add(
                    NodeExecutionTrace(
                        nodeId = queue.first(),
                        status = NodeExecutionStatus.FAILED_TERMINAL,
                        attempts = 0,
                        durationMicros = 0L,
                        detail = "Graph exceeded execution budget (${graph.maxExecutionTimeMillis}ms).",
                    ),
                )
                break
            }

            val nodeId = queue.removeFirst()
            if (!visited.add(nodeId)) continue
            val node = nodeMap[nodeId] ?: continue
            val nodeStart = clock.nowNanos()

            if (!evaluateGuard(node.guard, environment)) {
                traces.add(
                    NodeExecutionTrace(
                        nodeId = node.id,
                        status = NodeExecutionStatus.SKIPPED_BY_GUARD,
                        attempts = 0,
                        durationMicros = elapsedMicros(nodeStart),
                    ),
                )
                continue
            }

            val requiredPerm = ActionGraphValidator.requiredPermissionFor(node.command)
            if (requiredPerm != null && requiredPerm !in environment.grantedPermissions) {
                terminalFailure = true
                traces.add(
                    NodeExecutionTrace(
                        nodeId = node.id,
                        status = NodeExecutionStatus.BLOCKED_MISSING_PERMISSION,
                        attempts = 0,
                        durationMicros = elapsedMicros(nodeStart),
                        detail = "Missing required permission: ${requiredPerm.name}",
                    ),
                )
                node.fallbackNodeId?.let(queue::addLast)
                continue
            }

            val breakerKey = "${graph.id}:${node.id}"
            val breaker = nodeBreakers.computeIfAbsent(breakerKey) {
                CircuitBreaker(breakerFailureThreshold, breakerResetMillis, clock)
            }
            if (!breaker.allowExecution()) {
                traces.add(
                    NodeExecutionTrace(
                        nodeId = node.id,
                        status = NodeExecutionStatus.BLOCKED_CIRCUIT_OPEN,
                        attempts = 0,
                        durationMicros = elapsedMicros(nodeStart),
                        detail = "Circuit breaker open after repeated failures.",
                    ),
                )
                val fallbackNodeId = node.fallbackNodeId
                if (fallbackNodeId != null) {
                    queue.addLast(fallbackNodeId)
                } else {
                    terminalFailure = true
                }
                continue
            }

            // Acquire anti-loop token prior to dispatching synthetic Accessibility gestures
            val cmd = node.command
            if (cmd is ActionCommand.SynthesizeGesture) {
                val spanMs = cmd.strokes.maxOfOrNull {
                    it.startTimeMillis + it.durationMillis
                } ?: 200L
                syntheticToken = syntheticGuard.acquireDispatchWindow(spanMs)
            }

            val (succeeded, attempts, errorDetail) = runWithRetry(node)
            if (succeeded) {
                breaker.recordSuccess()
                traces.add(
                    NodeExecutionTrace(
                        nodeId = node.id,
                        status = NodeExecutionStatus.SUCCEEDED,
                        attempts = attempts,
                        durationMicros = elapsedMicros(nodeStart),
                    ),
                )
                node.nextNodeIds.forEach(queue::addLast)
            } else {
                breaker.recordFailure()
                val fallback = node.fallbackNodeId
                if (fallback != null) {
                    traces.add(
                        NodeExecutionTrace(
                            nodeId = node.id,
                            status = NodeExecutionStatus.FAILED_RECOVERED_BY_FALLBACK,
                            attempts = attempts,
                            durationMicros = elapsedMicros(nodeStart),
                            detail = errorDetail,
                        ),
                    )
                    queue.addLast(fallback)
                } else {
                    terminalFailure = true
                    traces.add(
                        NodeExecutionTrace(
                            nodeId = node.id,
                            status = NodeExecutionStatus.FAILED_TERMINAL,
                            attempts = attempts,
                            durationMicros = elapsedMicros(nodeStart),
                            detail = errorDetail,
                        ),
                    )
                }
            }
        }

        return ExecutionReport(
            graphId = graph.id,
            triggerRuleId = triggerRuleId,
            overallSuccess = !terminalFailure,
            traces = traces,
            totalDurationMicros = elapsedMicros(startNanos),
            syntheticGestureToken = syntheticToken,
        )
    }

    private fun runWithRetry(node: ActionNode): Triple<Boolean, Int, String?> {
        val maxAttempts = 1 + node.maxRetries.coerceIn(0, 3)
        var lastError: String? = null
        for (attempt in 1..maxAttempts) {
            val outcome = try {
                executor.execute(node.command, node.timeoutMillis)
            } catch (ex: Exception) {
                AppResult.Err(
                    GestureError.PlatformFailure(
                        code = ex::class.simpleName ?: "UnknownException",
                        message = ex.message ?: "Unhandled executor exception",
                    ),
                )
            }
            when (outcome) {
                is AppResult.Ok -> return Triple(true, attempt, null)
                is AppResult.Err -> lastError = outcome.error.toString()
            }
        }
        return Triple(false, maxAttempts, lastError)
    }

    /**
     * Evaluates a guard against the runtime environment (pure, exhaustively tested).
     */
    fun evaluateGuard(guard: ActionGuard, env: ExecutionEnvironment): Boolean = when (guard) {
        ActionGuard.Always -> true
        is ActionGuard.BatteryAtLeast -> env.batteryPercent >= guard.minPercent
        is ActionGuard.ForegroundPackageIn -> env.foregroundPackage != null && env.foregroundPackage in guard.packages
        is ActionGuard.ForegroundPackageNotIn -> env.foregroundPackage == null ||
            env.foregroundPackage !in guard.packages
        is ActionGuard.TimeWindow -> {
            val h = env.hourOfDay.coerceIn(0, 23)
            if (guard.startHour <= guard.endHour) {
                h in guard.startHour..guard.endHour
            } else {
                // Overnight window (e.g., 22..6)
                h >= guard.startHour || h <= guard.endHour
            }
        }
        ActionGuard.DeviceUnlocked -> env.isDeviceUnlocked
        ActionGuard.HeadsetConnected -> env.isHeadsetConnected
        is ActionGuard.AllOf -> guard.guards.all { evaluateGuard(it, env) }
    }

    private fun elapsedMicros(startNanos: Long): Long =
        ((clock.nowNanos() - startNanos) / 1_000L).coerceAtLeast(0L)
}

package com.vodiwalker.gesture.core.database

import com.vodiwalker.gesture.core.common.Json
import com.vodiwalker.gesture.core.common.JsonCodec
import com.vodiwalker.gesture.core.common.JsonNode
import com.vodiwalker.gesture.core.common.array
import com.vodiwalker.gesture.core.common.bool
import com.vodiwalker.gesture.core.common.field
import com.vodiwalker.gesture.core.common.float
import com.vodiwalker.gesture.core.common.int
import com.vodiwalker.gesture.core.common.jsonObjectOf
import com.vodiwalker.gesture.core.common.long
import com.vodiwalker.gesture.core.common.string
import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.ActionGuard
import com.vodiwalker.gesture.core.model.ActionNode
import com.vodiwalker.gesture.core.model.AppCategory
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GestureFeatureVector
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.HardwareToggleType
import com.vodiwalker.gesture.core.model.MediaControlType
import com.vodiwalker.gesture.core.model.NodeInteractionType
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.SyntheticStrokeSpec
import com.vodiwalker.gesture.core.model.SystemAccessibilityAction
import com.vodiwalker.gesture.core.model.TriggerScope

/**
 * Deterministic, allocation-conscious codecs mapping domain models to Room payload columns.
 *
 * Every decoder is **total and fail-soft**: malformed, truncated, or future-versioned payloads
 * degrade to defaults instead of throwing on the accessibility binder thread.
 */
object EntityMappers {

    // ------------------------------------------------------------------
    // GesturePoint list
    // ------------------------------------------------------------------

    /**
     * Encodes a point list as a compact, deterministic JSON array.
     */
    fun encodePoints(points: List<GesturePoint>): String {
        val array = Json.arr(
            points.map { point ->
                jsonObjectOf(
                    "x" to Json.float(point.x),
                    "y" to Json.float(point.y),
                    "t" to Json.long(point.timestampMillis),
                    "p" to Json.float(point.pressure),
                    "s" to Json.int(point.strokeId),
                )
            },
        )
        return JsonCodec.encode(array)
    }

    /**
     * Decodes a point array, skipping malformed entries instead of throwing.
     */
    fun decodePoints(raw: String): List<GesturePoint> {
        val array = JsonCodec.decodeOrNull(raw) as? JsonNode.Arr ?: return emptyList()
        return array.items.mapNotNull { node -> node.toGesturePoint() }
    }

    private fun JsonNode.toGesturePoint(): GesturePoint? {
        val x = this.float("x") ?: return null
        val y = this.float("y") ?: return null
        return GesturePoint(
            x = x,
            y = y,
            timestampMillis = this.long("t") ?: 0L,
            pressure = (this.float("p") ?: 1.0f).coerceIn(0f, 1f),
            strokeId = this.int("s") ?: 0,
        )
    }

    // ------------------------------------------------------------------
    // GestureFeatureVector
    // ------------------------------------------------------------------

    /**
     * Encodes the 16-field kinematic descriptor with short keys.
     */
    fun encodeFeatureVector(vector: GestureFeatureVector): String = JsonCodec.encode(
        jsonObjectOf(
            "bd" to Json.float(vector.boundingDiagonal),
            "ba" to Json.float(vector.boundingAngleRad),
            "ar" to Json.float(vector.aspectRatio),
            "pl" to Json.float(vector.pathLength),
            "sed" to Json.float(vector.startEndDistance),
            "sec" to Json.float(vector.startEndCos),
            "ses" to Json.float(vector.startEndSin),
            "ic" to Json.float(vector.initialCos),
            "is" to Json.float(vector.initialSin),
            "tta" to Json.float(vector.totalTurningAngleRad),
            "ata" to Json.float(vector.absoluteTurningAngleRad),
            "ss" to Json.float(vector.sharpnessSquaredRad),
            "pv" to Json.float(vector.peakVelocityPxPerMs),
            "mp" to Json.float(vector.meanPressure),
            "icnt" to Json.int(vector.inflectionCount),
            "sc" to Json.int(vector.strokeCount),
        ),
    )

    /**
     * Decodes the kinematic descriptor, defaulting missing fields to zero.
     */
    fun decodeFeatureVector(raw: String): GestureFeatureVector {
        val node = JsonCodec.decodeOrNull(raw)
        /**
         * Reads a float field, defaulting to 0 when absent.
         */
        fun value(key: String): Float = node.float(key) ?: 0f
        return GestureFeatureVector(
            boundingDiagonal = value("bd"),
            boundingAngleRad = value("ba"),
            aspectRatio = value("ar"),
            pathLength = value("pl"),
            startEndDistance = value("sed"),
            startEndCos = value("sec"),
            startEndSin = value("ses"),
            initialCos = value("ic"),
            initialSin = value("is"),
            totalTurningAngleRad = value("tta"),
            absoluteTurningAngleRad = value("ata"),
            sharpnessSquaredRad = value("ss"),
            peakVelocityPxPerMs = value("pv"),
            meanPressure = value("mp"),
            inflectionCount = node.int("icnt") ?: 0,
            strokeCount = node.int("sc") ?: 1,
        )
    }

    // ------------------------------------------------------------------
    // ActionNode graph payload
    // ------------------------------------------------------------------

    /**
     * Encodes the DAG node list (commands + guards) as JSON.
     */
    fun encodeNodes(nodes: List<ActionNode>): String =
        JsonCodec.encode(Json.arr(nodes.map { encodeNode(it) }))

    /**
     * Decodes DAG nodes, dropping entries whose command payload is invalid.
     */
    fun decodeNodes(raw: String): List<ActionNode> {
        val array = JsonCodec.decodeOrNull(raw) as? JsonNode.Arr ?: return emptyList()
        return array.items.mapNotNull { node -> decodeNode(node) }
    }

    private fun encodeNode(node: ActionNode): JsonNode {
        val entries = linkedMapOf(
            "id" to Json.str(node.id),
            "cmd" to encodeCommand(node.command),
            "next" to Json.strs(node.nextNodeIds),
            "timeout" to Json.long(node.timeoutMillis),
            "retries" to Json.int(node.maxRetries),
            "backoff" to Json.long(node.retryBackoffMillis),
            "guard" to encodeGuard(node.guard),
        )
        node.fallbackNodeId?.let { entries["fb"] = Json.str(it) }
        return Json.obj(entries)
    }

    private fun decodeNode(node: JsonNode): ActionNode? {
        val id = node.string("id") ?: return null
        val command = node.field("cmd")?.let { decodeCommand(it) } ?: return null
        val next = node.array("next").orEmpty().mapNotNull { item ->
            (item as? JsonNode.Str)?.value
        }
        return ActionNode(
            id = id,
            command = command,
            nextNodeIds = next,
            fallbackNodeId = node.string("fb"),
            guard = node.field("guard")?.let { decodeGuard(it) } ?: ActionGuard.Always,
            timeoutMillis = node.long("timeout") ?: 1_500L,
            maxRetries = node.int("retries") ?: 0,
            retryBackoffMillis = node.long("backoff") ?: 80L,
        )
    }

    private fun encodeCommand(command: ActionCommand): JsonNode = when (command) {
        is ActionCommand.GlobalSystemAction -> jsonObjectOf(
            "t" to Json.str("GLOBAL"),
            "action" to Json.str(command.action.name),
        )

        is ActionCommand.SynthesizeGesture -> jsonObjectOf(
            "t" to Json.str("SYNTH"),
            "strokes" to Json.arr(
                command.strokes.map { stroke ->
                    jsonObjectOf(
                        "pts" to (JsonCodec.decodeOrNull(encodePoints(stroke.waypoints)) ?: Json.arr(emptyList())),
                        "start" to Json.long(stroke.startTimeMillis),
                        "dur" to Json.long(stroke.durationMillis),
                        "cont" to Json.bool(stroke.willContinue),
                    )
                },
            ),
        )

        is ActionCommand.LaunchApplication -> jsonObjectOf(
            "t" to Json.str("LAUNCH"),
            "pkg" to Json.str(command.packageName),
            "clearTop" to Json.bool(command.clearTop),
        )

        is ActionCommand.OpenDeepLink -> jsonObjectOf(
            "t" to Json.str("DEEPLINK"),
            "uri" to Json.str(command.uri),
            "pkg" to Json.str(command.targetPackage),
        )

        is ActionCommand.MediaTransport -> jsonObjectOf(
            "t" to Json.str("MEDIA"),
            "control" to Json.str(command.control.name),
        )

        is ActionCommand.ToggleHardware -> jsonObjectOf(
            "t" to Json.str("HARDWARE"),
            "toggle" to Json.str(command.toggle.name),
        )

        is ActionCommand.SemanticNodeAction -> jsonObjectOf(
            "t" to Json.str("NODE"),
            "viewId" to Json.str(command.viewIdResourceName),
            "interaction" to Json.str(command.interaction.name),
        )

        is ActionCommand.EmitHaptic -> jsonObjectOf(
            "t" to Json.str("HAPTIC"),
            "preset" to Json.str(command.preset.name),
        )

        is ActionCommand.WaitDelay -> jsonObjectOf(
            "t" to Json.str("WAIT"),
            "ms" to Json.long(command.durationMillis),
        )
    }

    private fun decodeCommand(node: JsonNode): ActionCommand? = when (node.string("t")) {
        "GLOBAL" -> node.string("action")
            ?.enumOrNull<SystemAccessibilityAction>()
            ?.let { ActionCommand.GlobalSystemAction(it) }

        "SYNTH" -> ActionCommand.SynthesizeGesture(
            node.array("strokes").orEmpty().mapNotNull { stroke ->
                val points = stroke.array("pts").orEmpty().mapNotNull { it.toGesturePoint() }
                SyntheticStrokeSpec(
                    waypoints = points,
                    startTimeMillis = stroke.long("start") ?: 0L,
                    durationMillis = stroke.long("dur") ?: 180L,
                    willContinue = stroke.bool("cont") ?: false,
                )
            },
        )

        "LAUNCH" -> node.string("pkg")?.let {
            ActionCommand.LaunchApplication(packageName = it, clearTop = node.bool("clearTop") ?: false)
        }

        "DEEPLINK" -> node.string("uri")?.let {
            ActionCommand.OpenDeepLink(uri = it, targetPackage = node.string("pkg"))
        }

        "MEDIA" -> node.string("control")
            ?.enumOrNull<MediaControlType>()
            ?.let { ActionCommand.MediaTransport(it) }

        "HARDWARE" -> node.string("toggle")
            ?.enumOrNull<HardwareToggleType>()
            ?.let { ActionCommand.ToggleHardware(it) }

        "NODE" -> {
            val viewId = node.string("viewId")
            val interaction = node.string("interaction")?.enumOrNull<NodeInteractionType>()
            if (viewId != null && interaction != null) {
                ActionCommand.SemanticNodeAction(viewId, interaction)
            } else {
                null
            }
        }

        "HAPTIC" -> ActionCommand.EmitHaptic(
            node.string("preset")?.enumOrNull<HapticPreset>() ?: HapticPreset.CONFIRM_CRISP,
        )

        "WAIT" -> ActionCommand.WaitDelay(node.long("ms") ?: 0L)

        else -> null
    }

    private fun encodeGuard(guard: ActionGuard): JsonNode = when (guard) {
        ActionGuard.Always -> jsonObjectOf("t" to Json.str("ALWAYS"))

        is ActionGuard.BatteryAtLeast -> jsonObjectOf(
            "t" to Json.str("BATTERY"),
            "min" to Json.int(guard.minPercent),
        )

        is ActionGuard.ForegroundPackageIn -> jsonObjectOf(
            "t" to Json.str("PKG_IN"),
            "pkgs" to Json.strs(guard.packages),
        )

        is ActionGuard.ForegroundPackageNotIn -> jsonObjectOf(
            "t" to Json.str("PKG_NOT_IN"),
            "pkgs" to Json.strs(guard.packages),
        )

        is ActionGuard.TimeWindow -> jsonObjectOf(
            "t" to Json.str("TIME"),
            "start" to Json.int(guard.startHour),
            "end" to Json.int(guard.endHour),
        )

        ActionGuard.DeviceUnlocked -> jsonObjectOf("t" to Json.str("UNLOCKED"))

        ActionGuard.HeadsetConnected -> jsonObjectOf("t" to Json.str("HEADSET"))

        is ActionGuard.AllOf -> jsonObjectOf(
            "t" to Json.str("ALL_OF"),
            "guards" to Json.arr(guard.guards.map { encodeGuard(it) }),
        )
    }

    private fun decodeGuard(node: JsonNode): ActionGuard = when (node.string("t")) {
        "BATTERY" -> ActionGuard.BatteryAtLeast(node.int("min") ?: 0)
        "PKG_IN" -> ActionGuard.ForegroundPackageIn(node.stringSet("pkgs"))
        "PKG_NOT_IN" -> ActionGuard.ForegroundPackageNotIn(node.stringSet("pkgs"))
        "TIME" -> ActionGuard.TimeWindow(
            startHour = node.int("start") ?: 0,
            endHour = node.int("end") ?: 23,
        )
        "UNLOCKED" -> ActionGuard.DeviceUnlocked
        "HEADSET" -> ActionGuard.HeadsetConnected
        "ALL_OF" -> ActionGuard.AllOf(node.array("guards").orEmpty().map { decodeGuard(it) })
        else -> ActionGuard.Always
    }

    private fun JsonNode.stringSet(key: String): Set<String> =
        array(key).orEmpty().mapNotNull { (it as? JsonNode.Str)?.value }.toSet()

    private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    // ------------------------------------------------------------------
    // Domain <-> Entity
    // ------------------------------------------------------------------

    /**
     * Maps a domain template to its Room row.
     */
    fun GestureTemplate.toEntity(): GestureTemplateEntity = GestureTemplateEntity(
        id = id,
        name = name,
        category = category.name,
        pointsJson = encodePoints(normalizedPoints),
        featuresJson = encodeFeatureVector(featureVector),
        orientationSensitive = orientationSensitive,
        minConfidence = minConfidence,
        hapticPatternId = hapticPatternId,
        isEnabled = isEnabled,
        sampleCount = sampleCount,
        usageCount = usageCount,
        createdAtMillis = createdAtMillis,
    )

    /**
     * Maps a Room row back to the domain template.
     */
    fun GestureTemplateEntity.toDomain(): GestureTemplate = GestureTemplate(
        id = id,
        name = name,
        category = GestureCategory.entries.firstOrNull { it.name == category } ?: GestureCategory.CUSTOM,
        normalizedPoints = decodePoints(pointsJson),
        featureVector = decodeFeatureVector(featuresJson),
        orientationSensitive = orientationSensitive,
        minConfidence = minConfidence,
        hapticPatternId = hapticPatternId,
        isEnabled = isEnabled,
        sampleCount = sampleCount,
        usageCount = usageCount,
        createdAtMillis = createdAtMillis,
    )

    /**
     * Maps a domain graph to its Room row.
     */
    fun SmartActionGraph.toEntity(): ActionGraphEntity = ActionGraphEntity(
        id = id,
        name = name,
        description = description,
        entryNodeId = entryNodeId,
        nodesJson = encodeNodes(nodes),
        maxExecutionTimeMillis = maxExecutionTimeMillis,
    )

    /**
     * Maps a Room row back to a domain graph.
     */
    fun ActionGraphEntity.toDomain(): SmartActionGraph = SmartActionGraph(
        id = id,
        name = name,
        description = description,
        entryNodeId = entryNodeId,
        nodes = decodeNodes(nodesJson),
        maxExecutionTimeMillis = maxExecutionTimeMillis,
    )

    /**
     * Maps a trigger rule to its flat Room row (scope denormalized).
     */
    fun AppTriggerRule.toEntity(): AppTriggerRuleEntity {
        val scopeType = when (this.scope) {
            TriggerScope.Global -> "GLOBAL"
            is TriggerScope.Category -> "CATEGORY"
            is TriggerScope.Package -> "PACKAGE"
            is TriggerScope.Activity -> "ACTIVITY"
        }
        return AppTriggerRuleEntity(
            id = id,
            gestureId = gestureId,
            actionGraphId = actionGraphId,
            scopeType = scopeType,
            targetPackage = when (val activeScope = this.scope) {
                is TriggerScope.Package -> activeScope.packageName
                is TriggerScope.Activity -> activeScope.packageName
                else -> null
            },
            targetActivity = (this.scope as? TriggerScope.Activity)?.activityClassName,
            targetCategory = (this.scope as? TriggerScope.Category)?.category?.name,
            priority = priority,
            isEnabled = isEnabled,
            cooldownMillis = cooldownMillis,
            hapticOverrideId = hapticOverrideId,
        )
    }

    /**
     * Rebuilds the sealed [TriggerScope] from the flat row.
     */
    fun AppTriggerRuleEntity.toDomain(): AppTriggerRule = AppTriggerRule(
        id = id,
        gestureId = gestureId,
        actionGraphId = actionGraphId,
        scope = when (scopeType) {
            "CATEGORY" -> TriggerScope.Category(
                targetCategory
                    ?.let { raw -> AppCategory.entries.firstOrNull { it.name == raw } }
                    ?: AppCategory.OTHER,
            )
            "PACKAGE" -> TriggerScope.Package(targetPackage.orEmpty())
            "ACTIVITY" -> TriggerScope.Activity(
                packageName = targetPackage.orEmpty(),
                activityClassName = targetActivity.orEmpty(),
            )
            else -> TriggerScope.Global
        },
        priority = priority,
        isEnabled = isEnabled,
        cooldownMillis = cooldownMillis,
        hapticOverrideId = hapticOverrideId,
    )
}

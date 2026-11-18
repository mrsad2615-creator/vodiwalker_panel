# 04 · Core Code Snippets (Kotlin / Compose)

All snippets are excerpts of **compiling, tested** sources in this repository; file paths are given
so the full context can be read.

---

## 1. Hybrid recognition fusion — `:engine:gesture`

`HybridGestureEngine.recognize` fuses four orthogonal algorithms with weights that depend on stroke
count and orientation sensitivity.

```kotlin
// android/engine/gesture/.../HybridGestureEngine.kt
fun recognize(
    capture: GestureCapture,
    templates: List<GestureTemplate>,
    fullEnsemble: Boolean = true,
    isPrivacySensitiveWindow: Boolean = false,
): RecognitionOutcome {
    val startNanos = clock.nowNanos()

    if (isPrivacySensitiveWindow) {
        return RecognitionOutcome.Rejected(RejectionReason.PRIVACY_SHIELD_ACTIVE, null, elapsedMicros(startNanos))
    }
    if (syntheticGuard.isSyntheticWindowActive()) {
        return RecognitionOutcome.Rejected(RejectionReason.SYNTHETIC_LOOP_BLOCKED, null, elapsedMicros(startNanos))
    }

    val smoothed = synchronized(filter) { filter.filterSequence(capture.flattenPoints()) }
    val features = GestureGeometry.extractFeatures(smoothed)

    val ranked = enabledTemplates
        .map { template -> evaluateTemplate(smoothed, features, template, isMultiStroke, fullEnsemble) }
        .sortedByDescending { it.first.confidence }

    // Ambiguity is a first-class outcome: a 4.5% margin is the release gate.
    val (winner, runnerUp) = ranked.take(2)
    return if (winner.confidence < winner.template.minConfidence) {
        RecognitionOutcome.Rejected(RejectionReason.BELOW_CONFIDENCE_THRESHOLD, winner, totalMicros)
    } else if (runnerUp != null && winner.confidence - runnerUp.confidence < minDisambiguationMargin) {
        RecognitionOutcome.Rejected(RejectionReason.AMBIGUOUS_MARGIN, winner, totalMicros)
    } else {
        RecognitionOutcome.Matched(winner, ranked.drop(1).take(3), margin, totalMicros)
    }
}
```

## 2. Velocity-adaptive smoothing — `:engine:gesture/AdaptiveOneEuroFilter`

```kotlin
val rawDx = (clampedX - prevX) / dtSeconds
val hatDx = exponentialBlend(smoothingAlpha(dtSeconds, derivativeCutoffHz), rawDx, prevDx)

// fc = minCutoff + beta * |dx/dt|  →  slow moves smooth hard, fast swipes keep phase.
val cutoffX = minCutoffHz + beta * abs(hatDx)
val hatX = exponentialBlend(smoothingAlpha(dtSeconds, cutoffX), clampedX, prevX)
```

Verified properties: ≥38% tremor reduction, <8 px lag at 1 px/ms, 45 px/ms teleport clamp,
NaN/Inf/reversed-timestamp sanitization, <1.5 ms per sample at 480 samples/s.

## 3. Anti-recursion guard — `:core:common/SyntheticGestureGuard`

```kotlin
fun acquireDispatchWindow(durationMillis: Long): Long {
    val safeDuration = durationMillis.coerceIn(0L, 10_000L)
    val deadline = clock.nowMillis() + safeDuration + safetyMarginMillis
    suppressUntilMillis.updateAndGet { existing -> maxOf(existing, deadline) }
    return tokenCounter.getAndIncrement()
}

fun isSyntheticWindowActive(): Boolean = clock.nowMillis() < suppressUntilMillis.get()
```

## 4. Safe DAG execution — `:engine:smartaction/SmartActionEngine`

```kotlin
private fun runWithRetry(node: ActionNode): Triple<Boolean, Int, String?> {
    val maxAttempts = 1 + node.maxRetries.coerceIn(0, 3)
    var lastError: String? = null
    for (attempt in 1..maxAttempts) {
        val outcome = try {
            executor.execute(node.command, node.timeoutMillis)
        } catch (ex: Exception) {
            // A platform adapter may never crash the automation: exceptions become typed errors.
            AppResult.Err(GestureError.PlatformFailure(ex::class.simpleName ?: "UnknownException",
                ex.message ?: "Unhandled executor exception"))
        }
        when (outcome) {
            is AppResult.Ok -> return Triple(true, attempt, null)
            is AppResult.Err -> lastError = outcome.error.toString()
        }
    }
    return Triple(false, maxAttempts, lastError)
}
```

Per-node `CircuitBreaker` (`:core:common`) gates execution; `WaitDelay` goes through the injected
`Sleeper` port instead of `Thread.sleep`, so the engine stays deterministic under test.

## 5. Trigger specificity — `:engine:smartaction/AppTriggerResolver`

```kotlin
private fun specificityScore(scope: TriggerScope, env: ExecutionEnvironment): Int? = when (scope) {
    is TriggerScope.Activity -> if (env.foregroundPackage == scope.packageName &&
        env.foregroundActivity == scope.activityClassName) 400 else null
    is TriggerScope.Package  -> if (env.foregroundPackage == scope.packageName) 300 else null
    is TriggerScope.Category -> if (env.appCategory == scope.category) 200 else null
    TriggerScope.Global      -> 100
}
```

## 6. Power budget resolution — `:engine:power/AdaptivePowerGovernor`

```kotlin
fun evaluate(telemetry: DevicePowerTelemetry): PowerExecutionBudget = when {
    telemetry.isPrivacySensitiveWindow        -> build(PRIVACY_SHIELD_SUSPENDED, "Sensitive window")
    telemetry.isDeviceIdle || !telemetry.isScreenInteractive -> build(DOZE_SUSPENDED, "Doze / screen off")
    telemetry.thermalSeverity >= ThermalSeverity.SEVERE      -> build(ECO, "Thermal throttling")
    telemetry.isSystemPowerSaveMode            -> build(ECO, "System battery saver")
    telemetry.batteryPercent <= 10 && !telemetry.isCharging  -> build(ULTRA_SAVER, "Battery floor")
    telemetry.userPreferredMode != null        -> build(telemetry.userPreferredMode, "User preference")
    else -> build(BALANCED, "Nominal adaptive profile")
}
```

## 7. Three-tier haptics — `:engine:haptics/HapticChoreographer`

```kotlin
val scale = (userIntensity * powerScale).coerceIn(0f, 1f)
if (scale <= 0.02f) return CompiledHapticPlan.Suppressed
if (capabilities.apiLevel >= 31 && HapticPrimitiveType.SPIN in capabilities.supportedPrimitives) {
    return CompiledHapticPlan.RichComposition(steps.map { it.copy(scale = it.scale * scale) })
}
if (capabilities.hasAmplitudeControl) return CompiledHapticPlan.AmplitudeWaveform(timings, amplitudes)
return CompiledHapticPlan.PredefinedFallback(PredefinedHapticEffect.HEAVY_CLICK)
```

## 8. Room payload codec (fail-soft) — `:core:database/EntityMappers`

```kotlin
fun decodeNodes(raw: String): List<ActionNode> {
    val array = JsonCodec.decodeOrNull(raw) as? JsonNode.Arr ?: return emptyList()
    return array.items.mapNotNull { node -> decodeNode(node) }   // malformed rows are dropped
}

fun decodeGuard(node: JsonNode): ActionGuard = when (node.string("t")) {
    "BATTERY"  -> ActionGuard.BatteryAtLeast(node.int("min") ?: 0)
    "PKG_IN"   -> ActionGuard.ForegroundPackageIn(node.stringSet("pkgs"))
    "TIME"     -> ActionGuard.TimeWindow(node.int("start") ?: 0, node.int("end") ?: 23)
    "ALL_OF"   -> ActionGuard.AllOf(node.array("guards").orEmpty().map { decodeGuard(it) })
    else       -> ActionGuard.Always                     // unknown guard degrades to safe-by-default
}
```

## 9. Glassmorphic M3 component — `:core:designsystem`

```kotlin
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = VodiShapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
    contentPadding: Dp = VodiSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = LocalExtendedColors.current
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(containerColor, containerColor.copy(alpha = 0.84f)),
                ),
                shape,
            )
            .border(1.dp, extended.glassBorder, shape)
            .shadow(24.dp, shape, ambientColor = extended.inkGlow, spotColor = extended.inkGlow)
            .padding(contentPadding),
        content = content,
    )
}
```

## 10. Spring press physics — `:core:designsystem`

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val pressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.972f else 1f,
    animationSpec = VodiMotion.spatialFast(),
    label = "press-scale",
)
ClickableCard(onClick = onClick, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
```

## 11. Permission flow gating — `:feature:permissions`

```kotlin
Button(
    onClick = onContinue,
    enabled = state.isCoreAutomationReady,     // prominentDisclosure && accessibilityEnabled && canPerformGestures
    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("continue_to_studio_button"),
) { Text(if (state.isCoreAutomationReady) "Enter VodiGesture Studio" else "Complete Steps 1 & 2 to Activate") }
```

## 12. Predictive back + adaptive navigation — `:app/MainActivity`

```kotlin
PredictiveBackHandler(enabled = currentDestination != TopLevelDestination.DASHBOARD) { progressFlow ->
    try {
        progressFlow.collect { backEvent -> backScale = 1f - (0.08f * backEvent.progress) }
        backScale = 1f
        currentDestination = TopLevelDestination.DASHBOARD
    } catch (_: CancellationException) {
        backScale = 1f           // gesture cancelled: restore the unscaled shell
    }
}
```

## 13. Hilt engine wiring — `:app/MultiEngineModule`

```kotlin
@Provides @Singleton
fun provideHybridGestureEngine(
    guard: SyntheticGestureGuard,
    clock: MonotonicClock,
): HybridGestureEngine = HybridGestureEngine(syntheticGuard = guard, clock = clock)
```

## 14. Idle-only telemetry maintenance — `:app/TelemetryCompactionWorker`

```kotlin
val constraints = Constraints.Builder()
    .setRequiresBatteryNotLow(true)
    .setRequiresDeviceIdle(true)         // never wakes the CPU mid-day
    .build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<TelemetryCompactionWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints).build(),
)
```

## 15. Compose UI test — `:app/src/androidTest`

```kotlin
@Test
fun permissionFlowGatesAccessibilityStepUntilProminentDisclosureAccepted() {
    composeRule.setContent { VodiGestureTheme(darkTheme = true, dynamicColor = false) { /* … */ } }
    composeRule.onNodeWithTag("continue_to_studio_button").assertIsNotEnabled()
    composeRule.onNodeWithTag("step_accessibility_card").assertIsNotEnabled()
    composeRule.onNodeWithTag("step_disclosure_card").performClick()
    composeRule.onNodeWithText("Accessibility Service Disclosure").assertIsDisplayed()
    composeRule.onNodeWithTag("accept_disclosure_button").performClick()
    assertTrue(disclosureAccepted)
}
```

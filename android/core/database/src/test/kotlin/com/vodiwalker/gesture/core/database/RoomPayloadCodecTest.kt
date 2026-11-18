package com.vodiwalker.gesture.core.database

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
import com.vodiwalker.gesture.core.database.EntityMappers.toDomain
import com.vodiwalker.gesture.core.database.EntityMappers.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-path persistence suite for the Room payload codecs.
 *
 * The Room DAOs themselves require an Android device/Robolectric; everything that can corrupt an
 * on-device vault — JSON encoding, enum coercion, scope denormalization, forward-compatibility of
 * unknown fields — is pure Kotlin and is verified here against real domain objects.
 */
class RoomPayloadCodecTest {

    private val featureVector = GestureFeatureVector(
        boundingDiagonal = 512.5f,
        boundingAngleRad = 0.78f,
        aspectRatio = 0.42f,
        pathLength = 940.25f,
        startEndDistance = 210f,
        startEndCos = 0.31f,
        startEndSin = -0.95f,
        initialCos = 1f,
        initialSin = 0f,
        totalTurningAngleRad = 2.4f,
        absoluteTurningAngleRad = 3.9f,
        sharpnessSquaredRad = 1.7f,
        peakVelocityPxPerMs = 2.35f,
        meanPressure = 0.74f,
        inflectionCount = 2,
        strokeCount = 2,
    )

    private fun template(id: String = "gst_1") = GestureTemplate(
        id = id,
        name = "Cinema Split",
        category = GestureCategory.AUTOMATION_MACRO,
        normalizedPoints = List(12) { index ->
            GesturePoint(
                x = 0.05f * index,
                y = 0.5f + 0.01f * index,
                timestampMillis = index * 8L,
                pressure = 0.9f,
                strokeId = if (index < 6) 0 else 1,
            )
        },
        featureVector = featureVector,
        orientationSensitive = false,
        minConfidence = 0.81f,
        hapticPatternId = HapticPreset.AI_LOCK_IN.id,
        isEnabled = true,
        sampleCount = 3,
        usageCount = 42L,
        createdAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun gestureTemplateRoundTripsThroughRoomEntity() {
        val original = template()
        val restored = original.toEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.category, restored.category)
        assertEquals(original.orientationSensitive, restored.orientationSensitive)
        assertEquals(original.minConfidence, restored.minConfidence)
        assertEquals(original.hapticPatternId, restored.hapticPatternId)
        assertEquals(original.sampleCount, restored.sampleCount)
        assertEquals(original.usageCount, restored.usageCount)
        assertEquals(original.createdAtMillis, restored.createdAtMillis)
        assertEquals(original.normalizedPoints, restored.normalizedPoints)
        assertEquals(original.featureVector, restored.featureVector)
    }

    @Test
    fun smartActionGraphRoundTripsEveryCommandAndGuardVariant() {
        val commands = listOf(
            ActionCommand.GlobalSystemAction(SystemAccessibilityAction.TAKE_SCREENSHOT),
            ActionCommand.SynthesizeGesture(
                listOf(
                    SyntheticStrokeSpec(
                        waypoints = listOf(GesturePoint(10f, 20f, 0L), GesturePoint(300f, 900f, 120L)),
                        startTimeMillis = 40L,
                        durationMillis = 260L,
                        willContinue = true,
                    ),
                ),
            ),
            ActionCommand.LaunchApplication("com.example.browser", clearTop = true),
            ActionCommand.OpenDeepLink("https://example.org/watch?v=42", "com.example.player"),
            ActionCommand.MediaTransport(MediaControlType.NEXT_TRACK),
            ActionCommand.ToggleHardware(HardwareToggleType.TORCH_FLASHLIGHT),
            ActionCommand.SemanticNodeAction("com.example:id/play", NodeInteractionType.LONG_CLICK),
            ActionCommand.EmitHaptic(HapticPreset.CONFIRM_CINEMATIC),
            ActionCommand.WaitDelay(250L),
        )
        val guards = listOf(
            ActionGuard.Always,
            ActionGuard.BatteryAtLeast(25),
            ActionGuard.ForegroundPackageIn(setOf("com.example.a", "com.example.b")),
            ActionGuard.ForegroundPackageNotIn(setOf("com.bank")),
            ActionGuard.TimeWindow(22, 6),
            ActionGuard.DeviceUnlocked,
            ActionGuard.HeadsetConnected,
            ActionGuard.AllOf(listOf(ActionGuard.DeviceUnlocked, ActionGuard.BatteryAtLeast(10))),
            ActionGuard.DeviceUnlocked,
        )

        val graph = SmartActionGraph(
            id = "graph_cinema",
            name = "Cinema Routine",
            description = "Dim, launch player, play.",
            entryNodeId = "n0",
            nodes = commands.mapIndexed { index, command ->
                ActionNode(
                    id = "n$index",
                    command = command,
                    nextNodeIds = if (index < commands.lastIndex) listOf("n${index + 1}") else emptyList(),
                    fallbackNodeId = if (index == 3) "n${commands.lastIndex}" else null,
                    guard = guards[index],
                    timeoutMillis = 1_200L + index,
                    maxRetries = index % 3,
                    retryBackoffMillis = 60L + index,
                )
            },
            maxExecutionTimeMillis = 7_500L,
        )

        val restored = graph.toEntity().toDomain()

        assertEquals(graph.id, restored.id)
        assertEquals(graph.entryNodeId, restored.entryNodeId)
        assertEquals(graph.maxExecutionTimeMillis, restored.maxExecutionTimeMillis)
        assertEquals(graph.nodes.size, restored.nodes.size)
        graph.nodes.zip(restored.nodes).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.command, actual.command)
            assertEquals(expected.guard, actual.guard)
            assertEquals(expected.nextNodeIds, actual.nextNodeIds)
            assertEquals(expected.fallbackNodeId, actual.fallbackNodeId)
            assertEquals(expected.timeoutMillis, actual.timeoutMillis)
            assertEquals(expected.maxRetries, actual.maxRetries)
            assertEquals(expected.retryBackoffMillis, actual.retryBackoffMillis)
        }
    }

    @Test
    fun triggerRuleDenormalizesAndRestoresEveryScopeVariant() {
        val scopes = listOf(
            TriggerScope.Global,
            TriggerScope.Category(AppCategory.MEDIA_VIDEO),
            TriggerScope.Package("com.example.music"),
            TriggerScope.Activity("com.example.player", "com.example.player.PlayerActivity"),
        )

        scopes.forEachIndexed { index, scope ->
            val rule = AppTriggerRule(
                id = "rule_$index",
                gestureId = "gst_1",
                actionGraphId = "graph_cinema",
                scope = scope,
                priority = 120 - index,
                isEnabled = index % 2 == 0,
                cooldownMillis = 300L + index,
                hapticOverrideId = if (index == 2) HapticPreset.CONFIRM_CRISP.id else null,
            )
            val entity = rule.toEntity()
            val restored = entity.toDomain()

            assertEquals(rule, restored, "scope variant $scope must survive the flat schema")
            // Denormalization invariants asserted directly on the stored columns.
            when (scope) {
                TriggerScope.Global -> {
                    assertEquals("GLOBAL", entity.scopeType)
                    assertEquals(null, entity.targetPackage)
                }
                is TriggerScope.Category -> {
                    assertEquals("CATEGORY", entity.scopeType)
                    assertEquals(scope.category.name, entity.targetCategory)
                }
                is TriggerScope.Package -> {
                    assertEquals("PACKAGE", entity.scopeType)
                    assertEquals(scope.packageName, entity.targetPackage)
                }
                is TriggerScope.Activity -> {
                    assertEquals("ACTIVITY", entity.scopeType)
                    assertEquals(scope.activityClassName, entity.targetActivity)
                }
            }
        }
    }

    @Test
    fun corruptOrFuturePayloadsDegradeInsteadOfThrowing() {
        // Truncated JSON, wrong node types, unknown enum names, and unknown future keys.
        val hostileNodes = listOf(
            "",
            "{",
            "not json at all",
            """[{"id":"n0"}]""",
            """[{"id":"n1","cmd":{"t":"FUTURE_COMMAND"}}]""",
            """[{"id":"n2","cmd":{"t":"GLOBAL","action":"NOT_A_REAL_ACTION"}}]""",
            """[{"id":"n3","cmd":{"t":"WAIT","ms":10},"guard":{"t":"UNKNOWN_GUARD"},"extra":"future"}]""",
        )

        hostileNodes.forEach { payload ->
            val nodes = EntityMappers.decodeNodes(payload)
            // Only the well-formed-but-unknown-guard node survives; nothing throws.
            assertTrue(nodes.size <= 1, "unexpected recovery for payload: $payload")
            nodes.forEach { node ->
                assertEquals("n3", node.id)
                assertEquals(ActionGuard.Always, node.guard, "unknown guard must fall back to Always")
            }
        }

        assertTrue(EntityMappers.decodePoints("{oops").isEmpty())
        assertTrue(EntityMappers.decodePoints("").isEmpty())

        val degraded = EntityMappers.decodeFeatureVector("""{"bd":"not-a-number","futureField":1}""")
        assertEquals(0f, degraded.boundingDiagonal)
        assertEquals(1, degraded.strokeCount, "strokeCount floors at 1 for degenerate payloads")
    }
}

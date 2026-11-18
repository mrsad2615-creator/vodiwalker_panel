package com.vodiwalker.gesture.core.database

import com.vodiwalker.gesture.core.database.EntityMappers.toEntity
import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.ActionGuard
import com.vodiwalker.gesture.core.model.ActionNode
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.HardwareToggleType
import com.vodiwalker.gesture.core.model.MediaControlType
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.SystemAccessibilityAction
import com.vodiwalker.gesture.core.model.TriggerScope
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * First-run bootstrap that seeds a small, high-utility starter kit so a fresh install is useful
 * before the user records anything of their own.
 *
 * Design rules:
 * - **Idempotent**: seeding only runs when both stores are empty, so user deletions are never
 *   resurrected on the next launch.
 * - **Non-destructive**: every seeded template goes through the real
 *   [HybridGestureEngine.createCalibratedTemplate] pipeline, so collision detection and feature
 *   extraction behave exactly as they do for user recordings.
 * - **Fail-soft**: any repository/IO failure is swallowed (logged by the caller if desired) because
 *   a failed seed must never block app startup or crash the accessibility service.
 */
@Singleton
class PresetSeeder @Inject constructor(
    private val gestureEngine: HybridGestureEngine,
    private val templateDao: GestureTemplateDao,
    private val triggerAndGraphDao: TriggerAndGraphDao,
) {

    /**
     * Seeds the starter kit when the vault is empty.
     *
     * @return true when presets were written, false when the vault already had content or the
     *   seed aborted.
     */
    suspend fun seedIfEmpty(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return try {
            val existingGraphs = triggerAndGraphDao.getAllGraphs()
            val existingTemplates = templateDao.getAllTemplates()
            if (existingGraphs.isNotEmpty() || existingTemplates.isNotEmpty()) {
                return false
            }

            val graphs = PresetCatalog.graphs()
            graphs.forEach { graph -> triggerAndGraphDao.upsertGraph(graph.toEntity()) }

            val seededTemplates = mutableListOf<GestureTemplate>()
            for (spec in PresetCatalog.templates(nowMillis)) {
                val result = gestureEngine.createCalibratedTemplate(
                    id = spec.templateId,
                    name = spec.name,
                    category = spec.category,
                    calibrationCaptures = spec.captures,
                    existingTemplates = seededTemplates,
                    orientationSensitive = spec.orientationSensitive,
                    createdAtMillis = nowMillis,
                )
                val template = result.getOrNull() ?: continue
                val withHaptic = template.copy(hapticPatternId = spec.haptic.id)
                templateDao.upsert(withHaptic.toEntity())
                seededTemplates += withHaptic
            }

            PresetCatalog.rules().forEach { rule ->
                // Only bind rules whose graph and gesture both materialized.
                val gestureExists = seededTemplates.any { it.id == rule.gestureId }
                val graphExists = graphs.any { it.id == rule.actionGraphId }
                if (gestureExists && graphExists) {
                    triggerAndGraphDao.upsertRule(rule.toEntity())
                }
            }
            seededTemplates.isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Declarative definition of the built-in starter kit (three core graphs + five gesture bindings).
 *
 * Kept as a pure object so it can be unit tested and documented independently of persistence.
 */
object PresetCatalog {

    const val GRAPH_QUICK_CAPTURE = "graph_quick_capture"
    const val GRAPH_PLAYBACK_SHELF = "graph_playback_shelf"
    const val GRAPH_TORCH_SHORTCUT = "graph_torch_shortcut"

    const val TEMPLATE_SUMMON_SHIELD = "preset_summon_shield"
    const val TEMPLATE_SWIPE_LEFT = "preset_swipe_left"
    const val TEMPLATE_SWIPE_RIGHT = "preset_swipe_right"

    /** Data-only description of one seeded template sample set. */
    data class TemplateSpec(
        val templateId: String,
        val name: String,
        val category: GestureCategory,
        val captures: List<com.vodiwalker.gesture.core.model.GestureCapture>,
        val haptic: HapticPreset,
        val orientationSensitive: Boolean = true,
    )

    /** Synthesizes a straight swipe capture for a normalized unit direction. */
    private fun swipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        samples: Int = 24,
        durationMillis: Long = 160L,
    ): com.vodiwalker.gesture.core.model.GestureCapture {
        val points = List(samples) { index ->
            val t = index.toFloat() / (samples - 1).toFloat()
            GesturePoint(
                x = fromX + (toX - fromX) * t,
                y = fromY + (toY - fromY) * t,
                timestampMillis = (durationMillis * t).toLong(),
                strokeId = 0,
            )
        }
        return com.vodiwalker.gesture.core.model.GestureCapture(listOf(GestureStroke(0, points)))
    }

    /** Synthesizes a "Z"-shaped three-segment capture used for the privacy shield preset. */
    private fun zShape(): com.vodiwalker.gesture.core.model.GestureCapture {
        val points = mutableListOf<GesturePoint>()
        var t = 0L
        // Top bar, left → right
        for (i in 0 until 12) {
            points += GesturePoint(120f + i * 44f, 300f, t)
            t += 8L
        }
        // Diagonal, top-right → bottom-left
        for (i in 0 until 14) {
            points += GesturePoint(604f - i * 39f, 300f + i * 40f, t)
            t += 8L
        }
        // Bottom bar, left → right
        for (i in 0 until 12) {
            points += GesturePoint(110f + i * 44f, 860f, t)
            t += 8L
        }
        return com.vodiwalker.gesture.core.model.GestureCapture(listOf(GestureStroke(0, points)))
    }

    /** The starter template set with three calibration samples each. */
    fun templates(nowMillis: Long): List<TemplateSpec> = listOf(
        TemplateSpec(
            templateId = TEMPLATE_SWIPE_LEFT,
            name = "Back Swipe",
            category = GestureCategory.NAVIGATION,
            captures = listOf(
                swipe(900f, 1200f, 200f, 1210f),
                swipe(880f, 1180f, 190f, 1195f),
                swipe(910f, 1220f, 210f, 1225f),
            ),
            haptic = HapticPreset.CONFIRM_CRISP,
        ),
        TemplateSpec(
            templateId = TEMPLATE_SWIPE_RIGHT,
            name = "Forward Swipe",
            category = GestureCategory.NAVIGATION,
            captures = listOf(
                swipe(200f, 1200f, 900f, 1195f),
                swipe(190f, 1185f, 880f, 1180f),
                swipe(210f, 1225f, 910f, 1220f),
            ),
            haptic = HapticPreset.CONFIRM_CRISP,
        ),
        TemplateSpec(
            templateId = TEMPLATE_SUMMON_SHIELD,
            name = "Privacy Shield",
            category = GestureCategory.CUSTOM,
            captures = listOf(zShape(), zShape(), zShape()),
            haptic = HapticPreset.PRIVACY_SHIELD,
            orientationSensitive = false,
        ),
    )

    /** The starter Smart-Action graphs (each a valid, acyclic DAG). */
    fun graphs(): List<SmartActionGraph> = listOf(
        SmartActionGraph(
            id = GRAPH_QUICK_CAPTURE,
            name = "Quick Capture",
            description = "Haptic confirmation, screenshot, then a media-safe notification.",
            entryNodeId = "capture_0",
            nodes = listOf(
                ActionNode(
                    id = "capture_0",
                    command = ActionCommand.EmitHaptic(HapticPreset.CONFIRM_CINEMATIC),
                    nextNodeIds = listOf("capture_1"),
                    timeoutMillis = 300L,
                ),
                ActionNode(
                    id = "capture_1",
                    command = ActionCommand.GlobalSystemAction(SystemAccessibilityAction.TAKE_SCREENSHOT),
                    nextNodeIds = listOf("capture_2"),
                    timeoutMillis = 2_000L,
                    maxRetries = 1,
                ),
                ActionNode(
                    id = "capture_2",
                    command = ActionCommand.GlobalSystemAction(SystemAccessibilityAction.NOTIFICATIONS),
                    timeoutMillis = 800L,
                ),
            ),
            maxExecutionTimeMillis = 4_000L,
        ),
        SmartActionGraph(
            id = GRAPH_PLAYBACK_SHELF,
            name = "Playback Shelf",
            description = "Opens the media shelf and resumes playback; falls back to plain launch.",
            entryNodeId = "shelf_0",
            nodes = listOf(
                ActionNode(
                    id = "shelf_0",
                    command = ActionCommand.LaunchApplication("com.android.chrome"),
                    nextNodeIds = listOf("shelf_1"),
                    fallbackNodeId = "shelf_1",
                    guard = ActionGuard.BatteryAtLeast(15),
                    timeoutMillis = 1_200L,
                ),
                ActionNode(
                    id = "shelf_1",
                    command = ActionCommand.MediaTransport(MediaControlType.PLAY_PAUSE),
                    timeoutMillis = 600L,
                ),
            ),
            maxExecutionTimeMillis = 3_000L,
        ),
        SmartActionGraph(
            id = GRAPH_TORCH_SHORTCUT,
            name = "Torch Shortcut",
            description = "Toggles the camera torch, only while unlocked.",
            entryNodeId = "torch_0",
            nodes = listOf(
                ActionNode(
                    id = "torch_0",
                    command = ActionCommand.ToggleHardware(HardwareToggleType.TORCH_FLASHLIGHT),
                    guard = ActionGuard.DeviceUnlocked,
                    timeoutMillis = 900L,
                ),
            ),
            maxExecutionTimeMillis = 2_000L,
        ),
    )

    /** Starter bindings: global navigation plus one app-scoped and one category-scoped example. */
    fun rules(): List<AppTriggerRule> = listOf(
        AppTriggerRule(
            id = "rule_back_swipe",
            gestureId = TEMPLATE_SWIPE_LEFT,
            actionGraphId = GRAPH_QUICK_CAPTURE,
            scope = TriggerScope.Global,
            priority = 10,
            cooldownMillis = 800L,
        ),
        AppTriggerRule(
            id = "rule_forward_swipe",
            gestureId = TEMPLATE_SWIPE_RIGHT,
            actionGraphId = GRAPH_PLAYBACK_SHELF,
            scope = TriggerScope.Category(com.vodiwalker.gesture.core.model.AppCategory.MEDIA_VIDEO),
            priority = 20,
            cooldownMillis = 500L,
        ),
        AppTriggerRule(
            id = "rule_shield",
            gestureId = TEMPLATE_SUMMON_SHIELD,
            actionGraphId = GRAPH_TORCH_SHORTCUT,
            scope = TriggerScope.Global,
            priority = 5,
            cooldownMillis = 1_200L,
            hapticOverrideId = HapticPreset.PRIVACY_SHIELD.id,
        ),
    )
}

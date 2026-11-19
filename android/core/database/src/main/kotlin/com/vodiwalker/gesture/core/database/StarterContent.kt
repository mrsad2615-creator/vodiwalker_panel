package com.vodiwalker.gesture.core.database

import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.ActionNode
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.SystemAccessibilityAction
import com.vodiwalker.gesture.core.model.TriggerScope
import com.vodiwalker.gesture.engine.gesture.GestureGeometry

/**
 * Starter content installed once, on the first launch after setup.
 *
 * A gesture app that opens to an empty library has nothing to demonstrate, so the first run ships
 * three unambiguous, high-contrast shapes bound to the three actions people reach for most:
 *
 * | Gesture        | Action        |
 * | -------------- | ------------- |
 * | Upward stroke  | Home          |
 * | Leftward hook  | Back          |
 * | Downward stroke| Notifications |
 *
 * The shapes are generated as polylines (not recorded by hand) so they are identical on every
 * device and exercise exactly the same normalisation and feature-extraction path as a user-drawn
 * gesture. Starter content is intentionally *not* marked read-only: users can rename, disable or
 * delete any of it.
 */
object StarterContent {

    /** Stable identifiers keep the bootstrap idempotent across process restarts. */
    const val TEMPLATE_HOME = "gst_starter_home"
    const val TEMPLATE_BACK = "gst_starter_back"
    const val TEMPLATE_NOTIFICATIONS = "gst_starter_notifications"

    /** Graph identifiers for the one-node action graphs behind each starter gesture. */
    const val GRAPH_HOME = "graph_starter_home"
    const val GRAPH_BACK = "graph_starter_back"
    const val GRAPH_NOTIFICATIONS = "graph_starter_notifications"

    /** Rule identifiers binding each starter gesture globally. */
    const val RULE_HOME = "rule_starter_home"
    const val RULE_BACK = "rule_starter_back"
    const val RULE_NOTIFICATIONS = "rule_starter_notifications"

    /**
     * The three starter templates, fully normalised and feature-extracted.
     */
    fun templates(nowMillis: Long = 0L): List<GestureTemplate> = listOf(
        template(TEMPLATE_HOME, "خانه", GestureCategory.NAVIGATION, upwardStroke(), nowMillis),
        template(TEMPLATE_BACK, "بازگشت", GestureCategory.NAVIGATION, hookStroke(), nowMillis),
        template(
            TEMPLATE_NOTIFICATIONS,
            "اعلان‌ها",
            GestureCategory.SYSTEM_CONTROL,
            downwardStroke(),
            nowMillis,
        ),
    )

    /**
     * One-node graphs, one per starter gesture.
     */
    fun graphs(): List<SmartActionGraph> = listOf(
        graph(GRAPH_HOME, "خانه", "بازگشت به صفحهٔ اصلی", SystemAccessibilityAction.HOME),
        graph(GRAPH_BACK, "بازگشت", "یک صفحه عقب", SystemAccessibilityAction.BACK),
        graph(
            GRAPH_NOTIFICATIONS,
            "اعلان‌ها",
            "باز کردن پنل اعلان‌ها",
            SystemAccessibilityAction.NOTIFICATIONS,
        ),
    )

    /**
     * Global rules binding the starter gestures. Priority 0 keeps user-authored rules dominant.
     */
    fun rules(): List<AppTriggerRule> = listOf(
        rule(RULE_HOME, TEMPLATE_HOME, GRAPH_HOME),
        rule(RULE_BACK, TEMPLATE_BACK, GRAPH_BACK),
        rule(RULE_NOTIFICATIONS, TEMPLATE_NOTIFICATIONS, GRAPH_NOTIFICATIONS),
    )

    // ---------------------------------------------------------------------------------------
    // Shape generators. Coordinates are in a 0..1 box, converted to a 1080×2400 reference canvas
    // so path length and resampling behave like a real touch capture.
    // ---------------------------------------------------------------------------------------

    private const val REFERENCE_WIDTH_PX = 1080
    private const val REFERENCE_HEIGHT_PX = 2400
    private const val SAMPLE_COUNT = 24

    /** Bottom-to-top vertical stroke, the canonical "home" direction. */
    private fun upwardStroke(): List<GesturePoint> = polyline(
        points = List(SAMPLE_COUNT) { index ->
            val t = index / (SAMPLE_COUNT - 1f)
            0.5f to (0.78f - 0.56f * t)
        },
    )

    /** A left-to-right, then right-to-left hook — distinct from the straight vertical strokes. */
    private fun hookStroke(): List<GesturePoint> = polyline(
        points = List(SAMPLE_COUNT) { index ->
            val t = index / (SAMPLE_COUNT - 1f)
            // Half-circle opening toward the bottom, travelling left then back.
            val angle = Math.PI * (0.15f + 0.85f * t)
            val x = 0.62f - 0.26f * kotlin.math.cos(angle).toFloat()
            val y = 0.46f + 0.26f * kotlin.math.sin(angle).toFloat()
            x to y
        },
    )

    /** Top-to-bottom vertical stroke, the canonical "pull down the shade" direction. */
    private fun downwardStroke(): List<GesturePoint> = polyline(
        points = List(SAMPLE_COUNT) { index ->
            val t = index / (SAMPLE_COUNT - 1f)
            0.5f to (0.24f + 0.56f * t)
        },
    )

    private fun polyline(points: List<Pair<Float, Float>>): List<GesturePoint> =
        points.mapIndexed { index, (x, y) ->
            GesturePoint(
                x = x * REFERENCE_WIDTH_PX,
                y = y * REFERENCE_HEIGHT_PX,
                timestampMillis = index * 8L,
                pressure = 0.6f,
                strokeId = 0,
            )
        }

    private fun template(
        id: String,
        name: String,
        category: GestureCategory,
        rawPoints: List<GesturePoint>,
        nowMillis: Long,
    ): GestureTemplate {
        val normalized = GestureGeometry.normalizeToUnitSpace(rawPoints)
        return GestureTemplate(
            id = id,
            name = name,
            category = category,
            normalizedPoints = normalized,
            featureVector = GestureGeometry.extractFeatures(rawPoints),
            orientationSensitive = true,
            // Starter shapes are straight or clearly hooked, so a slightly stricter threshold is
            // safe and keeps false positives down.
            minConfidence = 0.74f,
            isEnabled = true,
            sampleCount = 1,
            usageCount = 0L,
            createdAtMillis = nowMillis,
        )
    }

    private fun graph(
        id: String,
        name: String,
        description: String,
        action: SystemAccessibilityAction,
    ): SmartActionGraph {
        val nodeId = "${id}_node"
        return SmartActionGraph(
            id = id,
            name = name,
            description = description,
            entryNodeId = nodeId,
            nodes = listOf(
                ActionNode(
                    id = nodeId,
                    command = ActionCommand.GlobalSystemAction(action),
                ),
            ),
            maxExecutionTimeMillis = 2_000L,
        )
    }

    private fun rule(id: String, gestureId: String, graphId: String): AppTriggerRule = AppTriggerRule(
        id = id,
        gestureId = gestureId,
        actionGraphId = graphId,
        scope = TriggerScope.Global,
        priority = 0,
        isEnabled = true,
        cooldownMillis = 350L,
    )
}

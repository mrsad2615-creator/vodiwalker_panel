package com.vodiwalker.gesture.core.database

import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GestureTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for the first-run library: it must be complete, distinct, and immediately usable
 * by the recogniser without any device interaction.
 */
class StarterContentTest {

    /** Every starter gesture ships with a template, a graph and a rule. */
    @Test
    fun starterContentIsComplete() {
        val templates = StarterContent.templates()
        val graphs = StarterContent.graphs()
        val rules = StarterContent.rules()

        assertEquals(3, templates.size)
        assertEquals(templates.size, graphs.size)
        assertEquals(templates.size, rules.size)

        val graphIds = graphs.map { it.id }.toSet()
        templates.forEach { template ->
            assertTrue(template.isEnabled, "${template.name} must ship enabled")
            assertTrue(template.normalizedPoints.isNotEmpty(), "${template.name} has no geometry")
            val rule = rules.firstOrNull { it.gestureId == template.id }
            assertNotNull(rule, "missing rule for ${template.name}")
            assertTrue(rule.actionGraphId in graphIds, "rule points at a missing graph")
        }
    }

    /** Each graph is a valid single-node automation that the engine can execute. */
    @Test
    fun graphsAreExecutable() {
        StarterContent.graphs().forEach { graph ->
            assertEquals(1, graph.nodes.size, "${graph.name} should be a single action")
            val entry = graph.nodes.firstOrNull { it.id == graph.entryNodeId }
            assertNotNull(entry, "${graph.name}: entry node missing")
            assertTrue(entry.nextNodeIds.isEmpty(), "${graph.name}: single node must be terminal")
            assertTrue(entry.fallbackNodeId == null, "${graph.name}: no fallback needed")
        }
    }

    /** Identifiers are stable so the installer stays idempotent across launches. */
    @Test
    fun identifiersAreStableAndUnique() {
        val ids = StarterContent.templates().map(GestureTemplate::id) +
            StarterContent.graphs().map { it.id } +
            StarterContent.rules().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "starter identifiers collide")
        assertEquals("gst_starter_home", StarterContent.TEMPLATE_HOME)
        assertEquals("graph_starter_back", StarterContent.GRAPH_BACK)
        assertEquals("rule_starter_notifications", StarterContent.RULE_NOTIFICATIONS)
    }

    /** The three shapes must be geometrically distinguishable, or recognition would be ambiguous. */
    @Test
    fun shapesAreDistinguishable() {
        val templates = StarterContent.templates()
        val verticalDirection = templates.map { template ->
            val points = template.normalizedPoints
            // Net vertical travel: strongly negative for up, positive for down, near zero for hook.
            points.last().y - points.first().y
        }
        // Home (up) and notifications (down) must travel in opposite directions.
        assertTrue(verticalDirection[0] < -0.5f, "home stroke must travel upward")
        assertTrue(verticalDirection[2] > 0.5f, "notifications stroke must travel downward")
        // The hook is horizontal-ish, so its vertical travel stays small.
        assertTrue(
            kotlin.math.abs(verticalDirection[1]) < 0.5f,
            "hook stroke must not read as a vertical swipe",
        )
    }

    /** Feature vectors must be populated, otherwise directional scoring cannot work. */
    @Test
    fun featureVectorsArePopulated() {
        StarterContent.templates().forEach { template ->
            assertTrue(template.featureVector.pathLength > 0f, "${template.name}: zero path length")
            assertTrue(
                template.featureVector.boundingDiagonal > 0f,
                "${template.name}: zero bounding diagonal",
            )
            assertTrue(template.minConfidence in 0.5f..0.95f)
        }
    }

    /** Starter gestures are user-facing content, so they are named in Persian. */
    @Test
    fun starterNamesArePersian() {
        StarterContent.templates().forEach { template ->
            assertTrue(
                template.name.any { it in '\u0600'..'\u06FF' },
                "${template.name} should use Persian script",
            )
        }
        assertEquals(GestureCategory.NAVIGATION, StarterContent.templates()[0].category)
    }
}

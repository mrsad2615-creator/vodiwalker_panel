package dev.mrsad.app

import dev.mrsad.app.ui.components.Core3D
import dev.mrsad.app.ui.components.CoreGeometry
import dev.mrsad.app.ui.components.CoreVec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The geometry of the MRSAD Core is checked without a device.
 *
 * The core is a hand-written renderer, so the shape it draws has to be
 * provably correct: a facet with an inverted normal would be lit from
 * the wrong side, and an edge that belongs to one facet instead of two
 * would leave a hole in the shell.
 */
class CoreGeometryTest {

    private fun distance(a: CoreVec3, b: CoreVec3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun theCoreIsAnIcosahedron() {
        assertEquals(12, CoreGeometry.vertices.size)
        assertEquals(20, CoreGeometry.faces.size)
        assertEquals(30, CoreGeometry.edges.size)
    }

    @Test
    fun everyVertexSitsOnTheUnitSphere() {
        CoreGeometry.vertices.forEach { vertex ->
            val length = sqrt(vertex.x * vertex.x + vertex.y * vertex.y + vertex.z * vertex.z)
            assertTrue(
                "the vertex $vertex is at $length instead of 1",
                abs(length - 1f) < 0.001f,
            )
        }
    }

    @Test
    fun everyFacetIsEquilateral() {
        CoreGeometry.faces.forEach { face ->
            val a = CoreGeometry.vertices[face[0]]
            val b = CoreGeometry.vertices[face[1]]
            val c = CoreGeometry.vertices[face[2]]
            val sides = listOf(distance(a, b), distance(b, c), distance(c, a))
            val longest = sides.max()
            val shortest = sides.min()
            assertTrue(
                "the facet $face is not equilateral: $sides",
                longest - shortest < 0.001f,
            )
        }
    }

    @Test
    fun everyFacetFacesOutwards() {
        CoreGeometry.faces.indices.forEach { index ->
            val normal = CoreGeometry.faceNormal(index)
            val face = CoreGeometry.faces[index]
            // The centre of the facet, in model space.
            val a = CoreGeometry.vertices[face[0]]
            val b = CoreGeometry.vertices[face[1]]
            val c = CoreGeometry.vertices[face[2]]
            val cx = (a.x + b.x + c.x) / 3f
            val cy = (a.y + b.y + c.y) / 3f
            val cz = (a.z + b.z + c.z) / 3f
            val dot = normal.x * cx + normal.y * cy + normal.z * cz
            assertTrue(
                "the facet $index points inwards (dot = $dot)",
                dot > 0f,
            )
        }
    }

    @Test
    fun everyEdgeIsSharedByExactlyTwoFacets() {
        CoreGeometry.edgeFaces.forEachIndexed { index, owners ->
            assertEquals(
                "the edge $index is not closed",
                2,
                owners.size,
            )
        }
    }

    @Test
    fun everyVertexIsUsedByTheSameNumberOfFacets() {
        val counts = IntArray(CoreGeometry.vertices.size)
        CoreGeometry.faces.forEach { face ->
            face.forEach { counts[it]++ }
        }
        counts.forEach { count ->
            assertEquals("a vertex is not used five times", 5, count)
        }
    }

    @Test
    fun theProjectionKeepsTheWholeObjectInFrontOfTheCamera() {
        val engine = Core3D(ringSegments = 24)
        var yaw = 0f
        while (yaw < 6.28f) {
            engine.update(
                yaw = yaw,
                pitch = 0.4f,
                roll = 0.1f,
                centerX = 100f,
                centerY = 100f,
                radiusPx = 50f,
            )
            for (i in 0 until engine.vertexCount) {
                val inFront = Core3D.CAMERA_DISTANCE - engine.screenZ[i]
                assertTrue(
                    "the vertex $i is behind the camera",
                    inFront > 0.5f,
                )
            }
            yaw += 0.37f
        }
    }

    @Test
    fun theFacetsAreDrawnBackToFront() {
        val engine = Core3D(ringSegments = 24)
        engine.update(
            yaw = 0.9f,
            pitch = -0.3f,
            roll = 0.2f,
            centerX = 0f,
            centerY = 0f,
            radiusPx = 40f,
        )
        for (i in 1 until engine.faceCount) {
            val previous = engine.faceDepth[engine.faceOrder[i - 1]]
            val current = engine.faceDepth[engine.faceOrder[i]]
            assertTrue(
                "the facets are not sorted back to front ($previous then $current)",
                previous <= current + 0.0001f,
            )
        }
    }

    @Test
    fun theProjectionScalesWithTheDistanceToTheEye() {
        val engine = Core3D(ringSegments = 24)
        engine.update(
            yaw = 0f,
            pitch = 0f,
            roll = 0f,
            centerX = 0f,
            centerY = 0f,
            radiusPx = 10f,
        )
        // The vertex that is nearest to the eye has to project further
        // from the centre than the one at the back.
        var nearest = 0
        var furthest = 0
        for (i in 1 until engine.vertexCount) {
            if (engine.screenZ[i] > engine.screenZ[nearest]) nearest = i
            if (engine.screenZ[i] < engine.screenZ[furthest]) furthest = i
        }
        val nearDistance = abs(engine.screenX[nearest] - 0f) + abs(engine.screenY[nearest] - 0f)
        val farDistance = abs(engine.screenX[furthest] - 0f) + abs(engine.screenY[furthest] - 0f)
        assertTrue(
            "the perspective does not change the size of the object",
            nearDistance > farDistance,
        )
    }
}

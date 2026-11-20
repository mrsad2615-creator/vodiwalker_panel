package dev.mrsad.app.ui.components

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point in the core's own three-dimensional space.
 *
 * The core is rendered by hand: there is no 3D library and no bitmap
 * anywhere in the pipeline. Every vertex is transformed, projected and
 * shaded by this file, which is why the whole engine is plain Kotlin and
 * can be tested without a device.
 */
data class CoreVec3(val x: Float, val y: Float, val z: Float)

/**
 * The shape of the MRSAD Core.
 *
 * The body is a regular icosahedron: twelve vertices, twenty triangular
 * facets and thirty edges. It was chosen over a sphere for three reasons.
 *
 *  - A sphere has to be *approximated* by triangles, and the seams of that
 *    approximation are visible as a faceted band. A polyhedron is exact:
 *    every facet is planar, so flat shading is correct rather than a
 *    compromise.
 *  - Twenty facets are cheap enough to redraw on every frame while still
 *    producing enough distinct normals for the light to read as a real
 *    material.
 *  - Its silhouette is unmistakably a *made* object, which is what a
 *    centrepiece has to be.
 *
 * The numbers below are the exact coordinates of the canonical icosahedron
 * with an edge length of two, normalised onto the unit sphere.
 */
object CoreGeometry {

    /** The twelve vertices, on the unit sphere. */
    val vertices: List<CoreVec3> = listOf(
        CoreVec3(-0.525731f, 0.850651f, 0.000000f),
        CoreVec3(0.525731f, 0.850651f, 0.000000f),
        CoreVec3(-0.525731f, -0.850651f, 0.000000f),
        CoreVec3(0.525731f, -0.850651f, 0.000000f),
        CoreVec3(0.000000f, -0.525731f, 0.850651f),
        CoreVec3(0.000000f, 0.525731f, 0.850651f),
        CoreVec3(0.000000f, -0.525731f, -0.850651f),
        CoreVec3(0.000000f, 0.525731f, -0.850651f),
        CoreVec3(0.850651f, 0.000000f, -0.525731f),
        CoreVec3(0.850651f, 0.000000f, 0.525731f),
        CoreVec3(-0.850651f, 0.000000f, -0.525731f),
        CoreVec3(-0.850651f, 0.000000f, 0.525731f),
    )

    /** The twenty facets, each wound counter-clockwise seen from outside. */
    val faces: List<IntArray> = listOf(
        intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7),
        intArrayOf(0, 7, 10), intArrayOf(0, 10, 11), intArrayOf(1, 5, 9),
        intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6),
        intArrayOf(7, 1, 8), intArrayOf(3, 9, 4), intArrayOf(3, 4, 2),
        intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10),
        intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
    )

    /** The thirty unique edges, each stored as its two vertex indices. */
    val edges: List<IntArray> = buildList {
        val seen = LinkedHashMap<Int, IntArray>()
        for (face in faces) {
            for (i in 0 until 3) {
                val a = face[i]
                val b = face[(i + 1) % 3]
                val key = if (a < b) a * 1000 + b else b * 1000 + a
                seen.getOrPut(key) { intArrayOf(minOf(a, b), maxOf(a, b)) }
            }
        }
        addAll(seen.values)
    }

    /** The two facets that meet at every edge, in the same order as [edges]. */
    val edgeFaces: List<IntArray> = edges.map { edge ->
        val a = edge[0]
        val b = edge[1]
        faces.indices
            .filter { index -> faces[index].contains(a) && faces[index].contains(b) }
            .take(2)
            .toIntArray()
    }

    /**
     * The outward normal of a facet, in model space.
     *
     * The winding of [faces] is counter-clockwise seen from outside, so the
     * normal points away from the centre and the light maths below does not
     * have to flip anything.
     */
    fun faceNormal(faceIndex: Int): CoreVec3 {
        val face = faces[faceIndex]
        val a = vertices[face[0]]
        val b = vertices[face[1]]
        val c = vertices[face[2]]
        val u = CoreVec3(b.x - a.x, b.y - a.y, b.z - a.z)
        val v = CoreVec3(c.x - a.x, c.y - a.y, c.z - a.z)
        return normalise(
            CoreVec3(
                u.y * v.z - u.z * v.y,
                u.z * v.x - u.x * v.z,
                u.x * v.y - u.y * v.x,
            )
        )
    }

    private fun normalise(v: CoreVec3): CoreVec3 {
        val length = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        if (length == 0f) return CoreVec3(0f, 0f, 1f)
        return CoreVec3(v.x / length, v.y / length, v.z / length)
    }
}

/**
 * The renderer of the MRSAD Core.
 *
 * The pipeline is the classic one, done by hand:
 *
 *  1. rotate the model by yaw, pitch and roll,
 *  2. project every vertex through a pinhole camera with a real
 *     perspective divide,
 *  3. shade every facet with a key light, a fill light, a specular term
 *     and a rim term,
 *  4. sort the facets back to front and let the renderer draw them.
 *
 * All the buffers are allocated once and reused for every frame, so the
 * renderer does not produce a single object per frame after the first one.
 * That is what keeps a full-screen 3D object inside the frame budget.
 */
class Core3D(val ringSegments: Int = 40) {

    val vertexCount: Int = CoreGeometry.vertices.size
    val faceCount: Int = CoreGeometry.faces.size

    /** The rotated model vertices, three floats each. */
    private val model = FloatArray(vertexCount * 3)

    /** The projected screen position of every vertex. */
    val screenX = FloatArray(vertexCount)
    val screenY = FloatArray(vertexCount)

    /** The camera-space depth of every vertex; larger is nearer. */
    val screenZ = FloatArray(vertexCount)

    /** How brightly every facet is lit, roughly 0..1. */
    val faceShade = FloatArray(faceCount)

    /** The specular highlight of every facet, 0..1. */
    val faceSpecular = FloatArray(faceCount)

    /** How much of the silhouette light every facet catches, 0..1. */
    val faceRim = FloatArray(faceCount)

    /** The average depth of every facet. */
    val faceDepth = FloatArray(faceCount)

    /** The facets, ordered back to front. */
    val faceOrder = IntArray(faceCount) { it }

    /** The projected points of both orbital rings, `ringSegments + 1` each. */
    val ringPointCount: Int = 2 * (ringSegments + 1)
    val ringX = FloatArray(ringPointCount)
    val ringY = FloatArray(ringPointCount)
    val ringZ = FloatArray(ringPointCount)

    /** The offset of the second ring inside [ringX] and friends. */
    val secondRingOffset: Int = ringSegments + 1

    /**
     * Transforms, projects and shades the whole scene.
     *
     * @param yaw rotation around the vertical axis, in radians
     * @param pitch rotation around the horizontal axis, in radians
     * @param roll rotation around the view axis, in radians
     * @param centerX the centre of the object on the canvas
     * @param centerY the centre of the object on the canvas
     * @param radiusPx the projected radius of a unit sphere at the centre
     */
    fun update(
        yaw: Float,
        pitch: Float,
        roll: Float,
        centerX: Float,
        centerY: Float,
        radiusPx: Float,
    ) {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)

        // The combined rotation matrix, Ry(yaw) * Rx(pitch) * Rz(roll).
        val m00 = cy * cr + sy * sp * sr
        val m01 = -cy * sr + sy * sp * cr
        val m02 = sy * cp
        val m10 = cp * sr
        val m11 = cp * cr
        val m12 = -sp
        val m20 = -sy * cr + cy * sp * sr
        val m21 = sy * sr + cy * sp * cr
        val m22 = cy * cp

        // The ring builder reuses the same matrix, so it is kept.
        matrix00 = m00
        matrix01 = m01
        matrix02 = m02
        matrix10 = m10
        matrix11 = m11
        matrix12 = m12
        matrix20 = m20
        matrix21 = m21
        matrix22 = m22

        // 1. Rotate the model.
        for (i in 0 until vertexCount) {
            val v = CoreGeometry.vertices[i]
            val x = v.x
            val y = v.y
            val z = v.z
            model[i * 3] = m00 * x + m01 * y + m02 * z
            model[i * 3 + 1] = m10 * x + m11 * y + m12 * z
            model[i * 3 + 2] = m20 * x + m21 * y + m22 * z
        }

        // 2. Project through a pinhole camera.
        for (i in 0 until vertexCount) {
            val x = model[i * 3]
            val y = model[i * 3 + 1]
            val z = model[i * 3 + 2]
            val scale = FOCAL / (CAMERA_DISTANCE - z)
            screenX[i] = centerX + x * scale * radiusPx
            screenY[i] = centerY - y * scale * radiusPx
            screenZ[i] = z
        }

        // 3. Shade every facet.
        for (f in 0 until faceCount) {
            val face = CoreGeometry.faces[f]
            val a = face[0] * 3
            val b = face[1] * 3
            val c = face[2] * 3
            val ux = model[b] - model[a]
            val uy = model[b + 1] - model[a + 1]
            val uz = model[b + 2] - model[a + 2]
            val vx = model[c] - model[a]
            val vy = model[c + 1] - model[a + 1]
            val vz = model[c + 2] - model[a + 2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            if (length > 0f) {
                nx /= length
                ny /= length
                nz /= length
            }

            val key = nx * KEY_LIGHT_X + ny * KEY_LIGHT_Y + nz * KEY_LIGHT_Z
            val fill = nx * FILL_LIGHT_X + ny * FILL_LIGHT_Y + nz * FILL_LIGHT_Z
            // The half vector between the key light and the eye, for the
            // specular term.
            val hx = KEY_LIGHT_X
            val hy = KEY_LIGHT_Y
            val hz = KEY_LIGHT_Z + 1f
            val hLength = sqrt(hx * hx + hy * hy + hz * hz)
            val specular = ((nx * hx + ny * hy + nz * hz) / hLength).coerceAtLeast(0f)

            faceShade[f] = (
                AMBIENT +
                    KEY_INTENSITY * key.coerceAtLeast(0f) +
                    FILL_INTENSITY * fill.coerceAtLeast(0f)
                ).coerceIn(0f, 1f)

            var powered = specular
            repeat(SPECULAR_POWER) { powered *= specular }
            faceSpecular[f] = powered

            val facing = (1f - nz.coerceIn(0f, 1f))
            faceRim[f] = facing * facing * facing
            faceDepth[f] = (model[a + 2] + model[b + 2] + model[c + 2]) / 3f
        }

        // 4. Sort the facets back to front. Twenty elements are sorted with
        //    an insertion sort: no allocation, no lambda, no boxing.
        for (i in 1 until faceCount) {
            val current = faceOrder[i]
            val currentDepth = faceDepth[current]
            var j = i - 1
            while (j >= 0 && faceDepth[faceOrder[j]] > currentDepth) {
                faceOrder[j + 1] = faceOrder[j]
                j--
            }
            faceOrder[j + 1] = current
        }

        // 5. The two orbital rings.
        buildRing(0, INNER_RING_RADIUS, INNER_RING_TILT, true)
        buildRing(secondRingOffset, OUTER_RING_RADIUS, OUTER_RING_TILT, false)
    }

    /**
     * Lays out one orbital ring and projects it.
     *
     * @param offset the first slot of the ring inside the ring buffers
     * @param radius the ring radius in model units
     * @param tilt how far the ring is tipped out of its own plane
     * @param aroundX true for the ring that turns around the horizontal axis
     */
    private fun buildRing(offset: Int, radius: Float, tilt: Float, aroundX: Boolean) {
        val sinTilt = sin(tilt)
        val cosTilt = cos(tilt)
        for (i in 0..ringSegments) {
            val angle = TWO_PI * i / ringSegments
            val sinA = sin(angle)
            val cosA = cos(angle)
            val lx: Float
            val ly: Float
            val lz: Float
            if (aroundX) {
                lx = cosA * radius
                ly = sinA * sinTilt * radius
                lz = sinA * cosTilt * radius
            } else {
                lx = sinA * sinTilt * radius
                ly = cosA * radius
                lz = sinA * cosTilt * radius
            }
            // The ring is rotated by the same matrix as the body, so it
            // turns with the core.
            val m00 = matrix00
            val m01 = matrix01
            val m02 = matrix02
            val m10 = matrix10
            val m11 = matrix11
            val m12 = matrix12
            val m20 = matrix20
            val m21 = matrix21
            val m22 = matrix22
            val x = m00 * lx + m01 * ly + m02 * lz
            val y = m10 * lx + m11 * ly + m12 * lz
            val z = m20 * lx + m21 * ly + m22 * lz
            val scale = FOCAL / (CAMERA_DISTANCE - z)
            ringX[offset + i] = x * scale
            ringY[offset + i] = -y * scale
            ringZ[offset + i] = z
        }
    }

    // The rotation matrix of the last update, kept so that the ring builder
    // does not have to recompute it.
    private var matrix00 = 1f
    private var matrix01 = 0f
    private var matrix02 = 0f
    private var matrix10 = 0f
    private var matrix11 = 1f
    private var matrix12 = 0f
    private var matrix20 = 0f
    private var matrix21 = 0f
    private var matrix22 = 1f

    /** The projected position of a ring point, in canvas pixels. */
    fun ringScreenX(offset: Int, index: Int, centerX: Float, radiusPx: Float): Float =
        centerX + ringX[offset + index] * radiusPx

    /** The projected position of a ring point, in canvas pixels. */
    fun ringScreenY(offset: Int, index: Int, centerY: Float, radiusPx: Float): Float =
        centerY + ringY[offset + index] * radiusPx

    companion object {
        const val TWO_PI: Float = 6.2831855f

        /** The distance of the eye from the origin. */
        const val CAMERA_DISTANCE: Float = 6.5f

        /** The focal length of the lens; larger means a flatter perspective. */
        const val FOCAL: Float = 5.6f

        /** The radius of the wide inner band. */
        const val INNER_RING_RADIUS: Float = 1.30f

        /** The radius of the thin outer ring. */
        const val OUTER_RING_RADIUS: Float = 1.68f

        /** How far the inner band is tipped out of its plane. */
        const val INNER_RING_TILT: Float = 0.38f

        /** How far the outer ring is tipped out of its plane. */
        const val OUTER_RING_TILT: Float = -0.58f

        private const val AMBIENT = 0.13f
        private const val KEY_INTENSITY = 0.70f
        private const val FILL_INTENSITY = 0.26f
        private const val SPECULAR_POWER = 22

        // A key light from the upper left and slightly in front.
        private const val KEY_LIGHT_X = -0.42f
        private const val KEY_LIGHT_Y = 0.62f
        private const val KEY_LIGHT_Z = 0.66f

        // A dim fill from the lower right, so the shadow side is not dead.
        private const val FILL_LIGHT_X = 0.68f
        private const val FILL_LIGHT_Y = -0.34f
        private const val FILL_LIGHT_Z = 0.30f
    }
}

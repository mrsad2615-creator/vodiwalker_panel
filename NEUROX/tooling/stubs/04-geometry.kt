// NEUROX offline compile stubs - geometry.
package androidx.compose.ui.geometry

class Offset(val x: Float, val y: Float) {
    companion object {
        val Zero = Offset(0f, 0f)
        val Infinite = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val Unspecified = Offset(Float.NaN, Float.NaN)
    }
}

class Size(val width: Float, val height: Float) {
    companion object {
        val Zero = Size(0f, 0f)
    }
}

class CornerRadius(val x: Float, val y: Float = x) {
    companion object {
        val Zero = CornerRadius(0f, 0f)
    }
}

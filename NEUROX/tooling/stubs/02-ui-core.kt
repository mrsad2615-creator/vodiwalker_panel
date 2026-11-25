// NEUROX offline compile stubs - androidx.compose.ui: Modifier, Alignment.
package androidx.compose.ui

/**
 * Modifier.
 *
 * The real type is a sealed interface plus a very large library of extension
 * functions spread across foundation/draw/layout. Only the extensions this app calls
 * are declared, each in its REAL package, so an import that would fail under Gradle
 * also fails here.
 */
interface Modifier {
    interface Element : Modifier

    infix fun then(other: Modifier): Modifier

    companion object : Modifier {
        override infix fun then(other: Modifier): Modifier = other
    }
}

/**
 * The used Alignment constants. The real type separates horizontal, vertical and
 * both-axis alignment with sealed subtypes; the app only passes these constants to
 * Row/Column/Box parameters, so one nominal type is enough to catch a typo.
 */
class Alignment private constructor() {
    companion object {
        val TopStart = Alignment()
        val TopCenter = Alignment()
        val TopEnd = Alignment()
        val CenterStart = Alignment()
        val Center = Alignment()
        val CenterEnd = Alignment()
        val End = Alignment()
        val Start = Alignment()
        val BottomStart = Alignment()
        val BottomCenter = Alignment()
        val BottomEnd = Alignment()
        val CenterHorizontally = Alignment()
        val CenterVertically = Alignment()
    }
}

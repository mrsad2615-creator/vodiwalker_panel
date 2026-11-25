// NEUROX offline compile stubs - interaction sources.
package androidx.compose.foundation.interaction

interface Interaction

interface InteractionSource

interface MutableInteractionSource : InteractionSource

class MutableInteractionSourceImpl : MutableInteractionSource

fun MutableInteractionSource(): MutableInteractionSource = MutableInteractionSourceImpl()

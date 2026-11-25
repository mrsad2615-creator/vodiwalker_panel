// NEUROX offline compile stubs - enter/exit transitions.
package androidx.compose.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface EnterTransition
interface ExitTransition

fun fadeIn(
    animationSpec: AnimationSpec<Float> = tween(),
    initialAlpha: Float = 0f,
): EnterTransition = object : EnterTransition {}

fun fadeOut(
    animationSpec: AnimationSpec<Float> = tween(),
    targetAlpha: Float = 0f,
): ExitTransition = object : ExitTransition {}

fun slideInHorizontally(
    animationSpec: AnimationSpec<androidx.compose.ui.unit.IntOffset> = tween(),
    initialOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): EnterTransition = object : EnterTransition {}

fun slideOutHorizontally(
    animationSpec: AnimationSpec<androidx.compose.ui.unit.IntOffset> = tween(),
    targetOffsetX: (fullWidth: Int) -> Int = { it / 2 },
): ExitTransition = object : ExitTransition {}

@Composable
fun AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {}

interface AnimatedVisibilityScope

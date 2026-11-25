// NEUROX offline compile stubs - Compose runtime.
//
// WHY: the sandbox cannot reach Google Maven, so real androidx cannot be resolved and
// every Compose file fails for reasons unrelated to our code. The old audit "solved"
// this by filtering errors out - which also hid real type errors. These stubs declare
// the platform surface the app actually uses, so the Android layers compile FOR REAL
// and every remaining diagnostic is a NEUROX bug.
//
// NOT A GRADLE SUBSTITUTE: the stubs are hand-written. If one disagrees with the real
// library, the audit can pass a call Gradle would reject. They are never packaged.
package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.EXPRESSION,
)
@Retention(AnnotationRetention.SOURCE)
annotation class Composable

@Retention(AnnotationRetention.BINARY)
annotation class Immutable

@Retention(AnnotationRetention.BINARY)
annotation class Stable

@Retention(AnnotationRetention.BINARY)
annotation class ReadOnlyComposable

interface State<out T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T
}

interface SnapshotMutationPolicy<T>

fun <T> mutableStateOf(value: T, policy: SnapshotMutationPolicy<T>? = null): MutableState<T> = error("stub")

operator fun <T> State<T>.getValue(thisObj: Any?, property: kotlin.reflect.KProperty<*>): T = value

operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
    this.value = value
}

@Composable
fun <T> remember(calculation: () -> T): T = calculation()

@Composable
fun <T> remember(key1: Any?, calculation: () -> T): T = calculation()

@Composable
fun <T> remember(key1: Any?, key2: Any?, calculation: () -> T): T = calculation()

@Composable
fun <T> remember(vararg keys: Any?, calculation: () -> T): T = calculation()

@Composable
fun LaunchedEffect(key1: Any?, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {}

@Composable
fun LaunchedEffect(vararg keys: Any?, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {}

class DisposableEffectScope {
    fun onDispose(onDisposeEffect: () -> Unit): DisposableEffectResult = DisposableEffectResult(onDisposeEffect)
}

class DisposableEffectResult(private val onDisposeEffect: () -> Unit = {}) {
    fun onDispose() = onDisposeEffect()
}

@Composable
fun DisposableEffect(key1: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult): DisposableEffectResult =
    DisposableEffectResult {}

@Composable
fun DisposableEffect(vararg keys: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult): DisposableEffectResult =
    DisposableEffectResult {}

@Composable
fun SideEffect(effect: () -> Unit) {}

@Composable
fun rememberCoroutineScope(): kotlinx.coroutines.CoroutineScope = error("stub")

@Composable
fun <T> rememberUpdatedState(newValue: T): State<T> = error("stub")

@Composable
fun withFrameNanos(onFrame: (frameTimeNanos: Long) -> Unit): Long = 0L

@Composable
fun key(vararg keys: Any?, block: @Composable () -> Unit) {}

class ProvidedValue<T>(val local: CompositionLocal<T>, val value: T)

abstract class CompositionLocal<T> {
    val current: T
        @Composable get() = error("stub")
}

class ProvidableCompositionLocal<T> : CompositionLocal<T>() {
    infix fun provides(value: T): ProvidedValue<T> = ProvidedValue(this, value)
}

fun <T> staticCompositionLocalOf(defaultFactory: () -> T): ProvidableCompositionLocal<T> = ProvidableCompositionLocal()

fun <T> compositionLocalOf(policy: SnapshotMutationPolicy<T>?, defaultFactory: () -> T): ProvidableCompositionLocal<T> =
    ProvidableCompositionLocal()

@Composable
fun CompositionLocalProvider(value: ProvidedValue<*>, content: @Composable () -> Unit) {}

@Composable
fun CompositionLocalProvider(vararg values: ProvidedValue<*>, content: @Composable () -> Unit) {}

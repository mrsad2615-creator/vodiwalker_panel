// NEUROX offline compile stubs - ui.platform composition locals.
package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.LifecycleOwner

val LocalLayoutDirection: ProvidableCompositionLocal<LayoutDirection> = staticCompositionLocalOf { LayoutDirection.Ltr }

val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner> = staticCompositionLocalOf { error("stub") }

val LocalDensity: ProvidableCompositionLocal<Density> = staticCompositionLocalOf { Density() }

val LocalContext: ProvidableCompositionLocal<android.content.Context> = staticCompositionLocalOf { error("stub") }

// NEUROX offline compile stubs - Material 3 components used by the app.
package androidx.compose.material3

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(Float.NaN),
    style: TextStyle = TextStyle(),
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    onTextLayout: ((Any) -> Unit)? = null,
) {}

@Composable
fun Text(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = TextStyle(),
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {}

class ButtonColors(val containerColor: Color, val contentColor: Color)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
    colors: ButtonColors = ButtonColors(Color.Unspecified, Color.Unspecified),
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: androidx.compose.ui.text.input.KeyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(),
    keyboardActions: Any? = null,
) {}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {}

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = Dp(4f),
) {}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
) {}

class ScaffoldPadding(val values: PaddingValues)

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    snackbarHost: @Composable (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    content: @Composable (PaddingValues) -> Unit,
) {}

// ---- Theme ----

class ColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

fun darkColorScheme(
    primary: Color = Color.Unspecified,
    onPrimary: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    background: Color = Color.Unspecified,
    onBackground: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    onSurface: Color = Color.Unspecified,
    surfaceVariant: Color = Color.Unspecified,
    onSurfaceVariant: Color = Color.Unspecified,
    error: Color = Color.Unspecified,
    outline: Color = Color.Unspecified,
): ColorScheme = ColorScheme(
    primary, onPrimary, secondary, background, onBackground, surface, onSurface,
    error, surfaceVariant, onSurfaceVariant, outline,
)

fun lightColorScheme(
    primary: Color = Color.Unspecified,
    onPrimary: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    background: Color = Color.Unspecified,
    onBackground: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    onSurface: Color = Color.Unspecified,
    surfaceVariant: Color = Color.Unspecified,
    onSurfaceVariant: Color = Color.Unspecified,
    error: Color = Color.Unspecified,
    outline: Color = Color.Unspecified,
): ColorScheme = ColorScheme(
    primary, onPrimary, secondary, background, onBackground, surface, onSurface,
    error, surfaceVariant, onSurfaceVariant, outline,
)

class Typography(
    val displayLarge: TextStyle = TextStyle(),
    val displayMedium: TextStyle = TextStyle(),
    val displaySmall: TextStyle = TextStyle(),
    val headlineLarge: TextStyle = TextStyle(),
    val headlineMedium: TextStyle = TextStyle(),
    val headlineSmall: TextStyle = TextStyle(),
    val titleLarge: TextStyle = TextStyle(),
    val titleMedium: TextStyle = TextStyle(),
    val titleSmall: TextStyle = TextStyle(),
    val bodyLarge: TextStyle = TextStyle(),
    val bodyMedium: TextStyle = TextStyle(),
    val bodySmall: TextStyle = TextStyle(),
    val labelLarge: TextStyle = TextStyle(),
    val labelMedium: TextStyle = TextStyle(),
    val labelSmall: TextStyle = TextStyle(),
)

/**
 * MaterialTheme.
 *
 * The real object exposes colorScheme/typography/shapes as composable properties backed
 * by composition locals. Modelling that here means `MaterialTheme.colorScheme.onSurface`
 * type-checks exactly as it does in the real library.
 */
object MaterialTheme {
    val colorScheme: ColorScheme
        @Composable get() = error("stub")

    val typography: Typography
        @Composable get() = error("stub")

    val shapes: Shapes
        @Composable get() = error("stub")
}

class Shapes(
    val extraSmall: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
    val small: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
    val medium: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
    val large: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
)

@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = darkColorScheme(),
    shapes: Shapes = Shapes(),
    typography: Typography = Typography(),
    content: @Composable () -> Unit,
) {}

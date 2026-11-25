// NEUROX offline compile stubs - gestures.
package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

suspend fun PointerInputScope.detectTapGestures(
    onDoubleTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onPress: (suspend (Offset) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
) {}

suspend fun PointerInputScope.detectDragGestures(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: Any, dragAmount: Offset) -> Unit,
) {}

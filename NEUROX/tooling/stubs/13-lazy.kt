// NEUROX offline compile stubs - lazy list layouts.
package androidx.compose.foundation.lazy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

interface LazyListScope {
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.() -> Unit)
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    )
}

interface LazyItemScope {
    fun Modifier.fillParentMaxSize(fraction: Float = 1f): Modifier
    fun Modifier.animateItem(): Modifier
}

inline fun <T> LazyListScope.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) = items(items.size, if (key != null) { index: Int -> key(items[index]) } else null) { index ->
    itemContent(items[index])
}

inline fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) = items(items.size, if (key != null) { index: Int -> key(index, items[index]) } else null) { index ->
    itemContent(index, items[index])
}

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement = Arrangement.Top,
    horizontalAlignment: Alignment = Alignment.TopStart,
    content: LazyListScope.() -> Unit,
) {}

@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement = Arrangement.Start,
    verticalAlignment: Alignment = Alignment.TopCenter,
    content: LazyListScope.() -> Unit,
) {}

class LazyListState {
    val firstVisibleItemIndex: Int get() = 0
    val firstVisibleItemScrollOffset: Int get() = 0
}

@Composable
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState = LazyListState()

@Composable
fun LazyListState.animateScrollToItem(index: Int) {}

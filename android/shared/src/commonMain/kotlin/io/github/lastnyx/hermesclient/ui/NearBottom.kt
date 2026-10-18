package io.github.lastnyx.hermesclient.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

/** The one rule for "the user is at the end of the list": last item visible and its end within [slackPx] of the viewport end. */
fun isNearBottom(totalItems: Int, lastVisibleIndex: Int?, lastVisibleItemEnd: Int, viewportEnd: Int, slackPx: Int): Boolean {
    if (totalItems == 0) return true
    if (lastVisibleIndex != totalItems - 1) return false
    return lastVisibleItemEnd - viewportEnd <= slackPx
}

fun LazyListState.isNearBottom(slackPx: Int): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    return isNearBottom(
        info.totalItemsCount,
        last?.index,
        if (last == null) 0 else last.offset + last.size,
        info.viewportEndOffset - info.afterContentPadding,
        slackPx,
    )
}

// scrollToItem alone would put the top of a tall last item at the top of the viewport.
suspend fun LazyListState.scrollToEnd() {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    scrollToItem(total - 1)
    scrollBy(1_000_000f)
}

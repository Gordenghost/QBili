package com.qbili.ui.screen.home

internal enum class HomeReselectAction { SCROLL_TO_TOP, REFRESH, NONE }

internal fun homeReselectAction(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    refreshLoading: Boolean,
): HomeReselectAction = when {
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 -> HomeReselectAction.SCROLL_TO_TOP
    refreshLoading -> HomeReselectAction.NONE
    else -> HomeReselectAction.REFRESH
}

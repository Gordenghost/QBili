package com.qbili.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.qbili.di.AppContainer

/** 依赖容器通过 CompositionLocal 下发，Composable 里 `LocalAppContainer.current` 即可取用 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 尚未提供，请检查 MainActivity 是否包裹了 CompositionLocalProvider")
}

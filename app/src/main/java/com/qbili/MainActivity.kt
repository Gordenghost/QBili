package com.qbili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbili.domain.model.ThemeMode
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.navigation.QBiliNavHost
import com.qbili.ui.theme.QBiliTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as QBiliApp).container

        setContent {
            val themeMode by container.themeSettingsStore.mode.collectAsStateWithLifecycle(initialValue = null)
            val mode = themeMode ?: ThemeMode.SYSTEM
            val systemDark = isSystemInDarkTheme()
            val darkTheme = mode.isDark(systemDark)
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1F.toInt()) { darkTheme },
                )
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                QBiliTheme(mode = mode, darkTheme = systemDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        if (themeMode != null) QBiliNavHost()
                    }
                }
            }
        }
    }
}

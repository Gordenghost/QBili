package com.qbili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.navigation.QBiliNavHost
import com.qbili.ui.theme.QBiliTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as QBiliApp).container

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                QBiliTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        QBiliNavHost()
                    }
                }
            }
        }
    }
}

package com.qbili.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbili.ui.LocalAppContainer
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPushSettingsClick: () -> Unit, onAboutClick: () -> Unit,
    onRecommendationSettingsClick: () -> Unit) {
    val source by LocalAppContainer.current.recommendationSettingsStore.source
        .collectAsStateWithLifecycle(initialValue = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("推荐算法") },
                supportingContent = { Text(source?.let { "当前：${it.title} · 点击切换推荐来源" } ?: "正在读取设置…") },
                leadingContent = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onRecommendationSettingsClick),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("推送设置") },
                supportingContent = { Text("管理首页推荐的标题关键词与 Tag 屏蔽") },
                leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onPushSettingsClick),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("检查更新、项目介绍与 GitHub 主页") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onAboutClick),
            )
        }
    }
}

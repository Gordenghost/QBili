package com.qbili.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qbili.BuildConfig
import com.qbili.core.AppInfo
import com.qbili.domain.model.UpdateCheckResult
import com.qbili.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: AboutViewModel = viewModel(
        factory = remember(container) { AboutViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("关于") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("QBili（轻哔）", style = MaterialTheme.typography.headlineSmall)
                val variant = if (BuildConfig.DEBUG) "Debug" else "Release"
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}） · $variant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "基于 Kotlin 与 Jetpack Compose 的 B 站第三方 Android 客户端。" +
                        "提供首页推荐、搜索、视频播放与弹幕、动态、UP 主空间、图文专栏、" +
                        "收藏和评论等功能，支持自定义推荐屏蔽。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "非官方开源项目，与哔哩哔哩无隶属、合作或授权关系。" +
                        "功能仍在开发，部分接口可能受平台限制或变更影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("检查更新") },
                supportingContent = {
                    Text(if (state.checking) "正在查询 GitHub 最新正式版本…" else "点击检查 GitHub Releases 中的新版本")
                },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                trailingContent = {
                    if (state.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable(enabled = !state.checking, onClick = viewModel::checkForUpdates),
            )
            ListItem(
                headlineContent = { Text("GitHub 项目主页") },
                supportingContent = { Text(AppInfo.REPOSITORY_URL) },
                leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                modifier = Modifier.clickable { openExternalLink(context, AppInfo.REPOSITORY_URL) },
            )
            ListItem(
                headlineContent = { Text("开源协议 · GPL-3.0-only") },
                supportingContent = { Text("查看完整许可证") },
                leadingContent = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
                modifier = Modifier.clickable { openExternalLink(context, AppInfo.LICENSE_URL) },
            )
            ListItem(
                headlineContent = { Text("免责声明") },
                supportingContent = { Text("了解非官方声明、账号风险及隐私注意事项") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                modifier = Modifier.clickable { openExternalLink(context, AppInfo.DISCLAIMER_URL) },
            )
        }
    }

    if (state.showResult) {
        UpdateResultDialog(state, viewModel::dismissResult) { url -> openExternalLink(context, url) }
    }
}

@Composable
private fun UpdateResultDialog(
    state: AboutViewModel.UiState,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val available = (state.result as? UpdateCheckResult.Available)?.release
    val title = when {
        state.error != null -> "检查更新失败"
        available != null -> "发现新版本 ${available.versionName}"
        state.result == UpdateCheckResult.NoPublishedRelease -> "暂无正式版本"
        else -> "已是最新版本"
    }
    val message = when {
        state.error != null -> state.error
        available != null -> "当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${available.versionName}\n\n" +
            if (available.downloadUrl != null) {
                "点击下载将在浏览器中打开 GitHub APK 下载链接。" +
                    "仅相同包名和签名的安装包可以覆盖更新。"
            } else {
                "该版本暂无与当前安装类型匹配的 APK，请打开 GitHub 发布页面查看。"
            }
        state.result == UpdateCheckResult.NoPublishedRelease -> "GitHub 尚未提供可用的正式发布版本。"
        else -> "当前版本：${BuildConfig.VERSION_NAME}\nGitHub 最新正式版本：" +
            (state.result as? UpdateCheckResult.UpToDate)?.latestVersionName.orEmpty() +
            "\n\n当前安装版本不低于最新正式发布版本，无需更新。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(message)
                if (available != null) {
                    Text(
                        available.downloadUrl ?: available.pageUrl,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onOpenLink(available.downloadUrl ?: available.pageUrl) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (available != null) {
                    onOpenLink(available.downloadUrl ?: available.pageUrl)
                } else if (state.error != null) {
                    onOpenLink(AppInfo.RELEASES_URL)
                }
                onDismiss()
            }) {
                Text(when {
                    available?.downloadUrl != null -> "下载 APK"
                    available != null || state.error != null -> "打开发布页面"
                    else -> "确定"
                })
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (available == null && state.error == null) onOpenLink(AppInfo.RELEASES_URL)
                onDismiss()
            }) {
                Text(if (available == null && state.error == null) "GitHub 发布页" else "关闭")
            }
        },
    )
}

private fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可打开链接的浏览器", Toast.LENGTH_LONG).show()
    } catch (error: SecurityException) {
        Toast.makeText(context, "无法打开浏览器，请稍后重试", Toast.LENGTH_LONG).show()
    }
}

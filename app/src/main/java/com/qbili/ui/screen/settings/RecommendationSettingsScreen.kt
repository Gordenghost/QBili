package com.qbili.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbili.core.friendlyMessage
import com.qbili.domain.model.RecommendationSource
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val store = container.recommendationSettingsStore
    val source by store.source.collectAsStateWithLifecycle(initialValue = null)
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    fun select(nextSource: RecommendationSource) {
        if (source == nextSource || saving || source == null) return
        saving = true
        scope.launch {
            val message = try {
                store.setSource(nextSource)
                "已切换为${nextSource.title}推荐，返回首页后显示新推荐"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                "保存失败：${error.friendlyMessage()}"
            } finally {
                saving = false
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("推荐算法") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup()) {
            item {
                Text("选择首页使用的 B 站推荐接口。切换后自动替换旧推荐并回到顶部，设置会保存在本机。",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            if (source == null) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            items(RecommendationSource.entries, key = { it.key }) { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = source == option,
                        enabled = source != null && !saving,
                        role = Role.RadioButton,
                        onClick = { select(option) },
                    ).padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.titleMedium)
                        Text(option.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = source == option, onClick = null, enabled = source != null && !saving)
                }
                HorizontalDivider()
            }
            item {
                Text("这里只切换推荐来源，不修改 B 站服务端的算法；标题、Tag、频道和 UP 屏蔽对两种来源都生效。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
                Text(when {
                    !cookies.isLoggedIn -> "当前未登录，App 端显示游客推荐。登录后会重新加载账号推荐和官方黑名单。"
                    container.hasAppRecommendationCredentials -> "App 端会校验凭据所属账号与有效期，使用当前移动端登录身份请求推荐。"
                    else -> "当前登录没有可用的 App 凭据。扫码、Cookie 和当前密码登录只有网页身份；请重新短信登录，或切换网页端。不会用游客推荐冒充账号推荐。"
                },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("首页会同步当前账号的完整官方黑名单，两种来源均过滤已拉黑 UP；下拉刷新会重新同步。若身份校验或黑名单同步失败，会显示错误，不会继续放行未校验的推荐。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            }
        }
    }
}

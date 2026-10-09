package com.qbili.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qbili.domain.model.CountryCode
import com.qbili.domain.model.QrLoginState
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.GeetestDialog
import com.qbili.ui.component.QrCodeImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: LoginViewModel = viewModel(factory = remember { LoginViewModel.factory(container) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // 进入页面默认停在扫码 Tab，直接把二维码拉起来
    LaunchedEffect(Unit) {
        if (state.tab == LoginViewModel.Tab.QR && state.qr is QrLoginState.Idle) {
            viewModel.startQrLogin()
        }
    }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    LaunchedEffect(state.info) {
        state.info?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissInfo()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val tabs = LoginViewModel.Tab.entries
            TabRow(selectedTabIndex = tabs.indexOf(state.tab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 登录失败的原因必须留在屏幕上：之前用 Snackbar，4 秒就消失了，
                // 用户只会觉得「没反应 / 短信没来」，根本看不到真实错误
                state.error?.let { message ->
                    ErrorBanner(message = message, onDismiss = viewModel::dismissError)
                    Spacer(Modifier.height(16.dp))
                }

                when (state.tab) {
                    LoginViewModel.Tab.QR -> QrLoginPane(
                        qr = state.qr,
                        onRefresh = viewModel::startQrLogin,
                    )

                    LoginViewModel.Tab.SMS -> SmsLoginPane(
                        state = state,
                        onCountryCodeChange = viewModel::onCountryCodeChange,
                        onPhoneChange = viewModel::onPhoneChange,
                        onSmsCodeChange = viewModel::onSmsCodeChange,
                        onRequestCode = viewModel::requestSmsCode,
                        onSubmit = viewModel::submitSmsLogin,
                    )

                    LoginViewModel.Tab.PASSWORD -> PasswordLoginPane(
                        state = state,
                        onUsernameChange = viewModel::onUsernameChange,
                        onPasswordChange = viewModel::onPasswordChange,
                        onSubmit = viewModel::submitPasswordLogin,
                    )

                    LoginViewModel.Tab.COOKIE -> CookieLoginPane(
                        state = state,
                        onTextChange = viewModel::onCookieTextChange,
                        onSubmit = viewModel::submitCookieLogin,
                    )
                }
            }
        }
    }

    state.pendingCaptcha?.let { pending ->
        GeetestDialog(
            gt = pending.gt,
            challenge = pending.challenge,
            onSuccess = viewModel::onCaptchaSuccess,
            onError = viewModel::onCaptchaError,
            onDismiss = viewModel::onCaptchaDismiss,
        )
    }
}

/**
 * Cookie 兜底登录。
 *
 * 极验和短信都可能被风控挡住，而登录是十几个功能的前置条件，
 * 所以留一条一定能走通的路：直接把浏览器里的 Cookie 搬过来。
 */
@Composable
private fun CookieLoginPane(
    state: LoginViewModel.UiState,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        "适用于扫码/短信都走不通的情况",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("获取步骤", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "1. 电脑浏览器登录 bilibili.com",
                "2. 按 F12 打开开发者工具，切到 Console",
                "3. 输入 document.cookie 回车",
                "4. 复制引号内的整段内容，粘贴到下面",
            ).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.cookieText,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth().height(140.dp),
        label = { Text("粘贴 Cookie") },
        placeholder = { Text("buvid3=...; SESSDATA=...; bili_jct=...; DedeUserID=...") },
        singleLine = false,
        maxLines = 6,
    )

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        enabled = state.canSubmitCookie,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("导入并登录")
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        "Cookie 只保存在本机应用私有目录，不会上传到任何第三方；导入后会立刻校验一次有效性",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(top = 1.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun QrLoginPane(
    qr: QrLoginState,
    onRefresh: () -> Unit,
) {
    // 二维码必须放在纯白底上，深色主题下直接铺 surface 会导致对比度不足扫不出来
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(240.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when (qr) {
                is QrLoginState.Ready -> QrCodeImage(
                    content = qr.content,
                    modifier = Modifier.size(216.dp),
                )

                is QrLoginState.Scanned -> {
                    QrCodeImage(content = qr.content, modifier = Modifier.size(216.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "已扫码\n请在手机上确认",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF1B1B1F),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                QrLoginState.Expired -> Text(
                    "二维码已失效",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1B1B1F),
                )

                QrLoginState.Success -> Text(
                    "登录成功",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1B1B1F),
                )

                is QrLoginState.Failed -> Text(
                    qr.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1B1B1F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )

                QrLoginState.Loading, QrLoginState.Idle -> CircularProgressIndicator()
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    Text(
        text = when (qr) {
            is QrLoginState.Ready -> "请用哔哩哔哩手机客户端扫码登录"
            is QrLoginState.Scanned -> "扫码成功，等待手机端确认"
            QrLoginState.Expired -> "二维码 3 分钟内有效，请点击下方按钮重新获取"
            QrLoginState.Success -> "正在进入…"
            is QrLoginState.Failed -> "获取二维码失败"
            else -> "正在获取二维码…"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    if (qr is QrLoginState.Expired || qr is QrLoginState.Failed) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重新获取")
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        "扫码登录不需要输入密码，也不会触发验证码，是最稳的方式",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SmsLoginPane(
    state: LoginViewModel.UiState,
    onCountryCodeChange: (CountryCode) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onSubmit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "使用手机短信验证码登录",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            textAlign = TextAlign.Center,
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(state.countryCode.dialCode)
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = "选择国际区号",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CountryCode.entries.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code.display) },
                        onClick = {
                            expanded = false
                            onCountryCodeChange(code)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            trailingIcon = {
                if (state.phone.isNotEmpty()) {
                    IconButton(onClick = { onPhoneChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空手机号")
                    }
                }
            },
        )
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.smsCode,
            onValueChange = onSmsCodeChange,
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onRequestCode,
            enabled = state.canSendSms,
        ) {
            if (state.smsCountdown > 0) {
                Text(
                    "等待${state.smsCountdown}秒",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("获取验证码")
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    Button(
        onClick = onSubmit,
        enabled = state.canSubmitSms,
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .align(Alignment.CenterHorizontally)
            .height(48.dp),
        shape = RoundedCornerShape(50),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("登录")
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        "手机号仅用于 bilibili 官方发送验证码与登录接口，不予保存；\n本地仅存储登录凭据",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    }
}

@Composable
private fun PasswordLoginPane(
    state: LoginViewModel.UiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("手机号 / 邮箱") },
        singleLine = true,
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onSubmit,
        enabled = state.canSubmitPassword,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("登录")
        }
    }

    Spacer(Modifier.height(16.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "密码只用于本地 RSA 加密后提交给 B 站，不会被存储",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "若提示需要安全验证，请先在官方端完成后再回来登录，或改用扫码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

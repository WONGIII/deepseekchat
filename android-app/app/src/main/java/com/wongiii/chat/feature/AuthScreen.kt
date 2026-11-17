package com.wongiii.chat.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wongiii.chat.data.local.normalizeServerUrl
import com.wongiii.chat.design.BrandLockup
import com.wongiii.chat.design.PrimaryButton
import com.wongiii.chat.design.SecondaryButton
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography

@Composable
fun AuthScreen(
    state: UiState,
    vm: ChatViewModel,
    onBack: () -> Unit,
) {
    val colors = chatColors()
    var server by rememberSaveable { mutableStateOf(state.serverUrl.orEmpty()) }
    var step by rememberSaveable { mutableStateOf(if (state.serverUrl != null && state.cloudUser != null) 1 else 0) }
    var username by rememberSaveable { mutableStateOf(state.cloudUser.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val showLogin = step == 1 || (state.serverUrl != null && state.authRequired && state.authError == null)

    Box(Modifier.fillMaxSize().background(colors.page)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.textPrimary)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandLockup(showTagline = true, markSize = 56.dp)
            Spacer(Modifier.height(34.dp))
            Text(
                text = if (showLogin) "登录" else "连接服务器",
                style = chatTypography().greeting.copy(fontSize = 28.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (showLogin) "登录后会话会按账号保存到你的服务器。" else "填写你的 Chat 服务地址，默认数据仍然保存在本机。",
                style = chatTypography().caption,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!showLogin) {
                    AuthField(
                        label = "服务器地址",
                        value = server,
                        onValueChange = { server = it; serverError = null },
                        placeholder = "http://192.168.0.216:8098",
                        keyboardType = KeyboardType.Uri,
                    )
                    if (serverError != null) {
                        Text(serverError.orEmpty(), color = colors.danger, style = chatTypography().caption)
                    }
                    PrimaryButton(
                        text = "连接",
                        onClick = {
                            val normalized = normalizeServerUrl(server)
                            if (normalized == null) {
                                serverError = "请输入有效的 http(s) 服务器地址"
                            } else {
                                vm.prepareCloud(normalized) { ok ->
                                    if (ok) step = 1 else serverError = state.authError ?: "无法连接服务器"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = state.authBusy,
                    )
                } else {
                    Text(
                        text = state.serverUrl ?: server,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.textTertiary,
                        style = chatTypography().caption,
                        textAlign = TextAlign.Center,
                    )
                    AuthField("用户名", username, { username = it }, "2-32 位字符")
                    AuthField(
                        label = "密码",
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "至少 6 位",
                        secret = true,
                    )
                    if (state.authError != null) {
                        Text(state.authError, color = colors.danger, style = chatTypography().caption)
                    }
                    PrimaryButton(
                        text = if (register) "注册并登录" else "登录",
                        onClick = { vm.login(username, password, register) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = username.trim().length >= 2 && password.length >= 6,
                        loading = state.authBusy,
                    )
                    TextButton(
                        onClick = { register = !register },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            if (register) "已有账号？登录" else "没有账号？注册",
                            color = colors.accent,
                            style = chatTypography().caption,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                SecondaryButton(
                    "继续使用本机模式",
                    onClick = {
                        vm.cancelCloudSetup()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    val colors = chatColors()
    Column {
        Text(label, style = chatTypography().caption, color = colors.textSecondary)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = chatTypography().caption, color = colors.textCaption) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

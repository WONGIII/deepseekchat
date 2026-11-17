package com.wongiii.chat.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wongiii.chat.data.local.AppSettings
import com.wongiii.chat.data.local.normalizeBaseUrl
import com.wongiii.chat.data.model.AccentColor
import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.BackgroundStyle
import com.wongiii.chat.data.model.Brand
import com.wongiii.chat.data.model.BubbleStyle
import com.wongiii.chat.data.model.FontSize
import com.wongiii.chat.data.model.HapticMode
import com.wongiii.chat.data.model.NetworkSettings
import com.wongiii.chat.data.model.ThemeMode
import com.wongiii.chat.data.model.TtsEmotion
import com.wongiii.chat.data.model.TtsVoice
import com.wongiii.chat.design.CardRadius
import com.wongiii.chat.design.ChatTheme
import com.wongiii.chat.design.ChoiceRow
import com.wongiii.chat.design.PrimaryButton
import com.wongiii.chat.design.SecondaryButton
import com.wongiii.chat.design.SectionLabel
import com.wongiii.chat.design.SettingsCard
import com.wongiii.chat.design.SettingsRow
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography
import com.wongiii.chat.domain.haptics.HapticLevels
import com.wongiii.chat.domain.haptics.StreamingHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI

enum class SettingsPage {
    ROOT,
    ACCOUNT,
    DATA,
    LANGUAGE,
    APPEARANCE,
    FONT_SIZE,
    PERSONALIZATION,
    HAPTIC,
    LOCATION,
    SERVICES,
    MODEL,
    NETWORK,
    ASR_LANGUAGE,
    TTS,
    UPDATE,
    TERMS,
    HELP,
    LICENSES,
}

@Composable
fun SettingsRoot(
    state: UiState,
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onOpenAuth: () -> Unit,
    onLeaveCloud: () -> Unit,
    rowModifier: (String) -> Modifier = { Modifier },
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val colors = chatColors()
    Column(Modifier.fillMaxSize().background(colors.settingsPage).navigationBarsPadding()) {
        SettingsTopBar(title = "设置", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("账户")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Person,
                    label = "账号管理",
                    labelModifier = rowModifier("account"),
                    value = state.cloudUser ?: "本机",
                    onClick = { onOpen(SettingsPage.ACCOUNT) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Storage,
                    label = "数据管理",
                    labelModifier = rowModifier("data"),
                    onClick = { onOpen(SettingsPage.DATA) },
                )
            }
            SectionLabel("应用")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    label = "语言",
                    labelModifier = rowModifier("language"),
                    value = if (state.settings.locale == "en-US") "English" else "中文（简体）",
                    onClick = { onOpen(SettingsPage.LANGUAGE) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = if (state.settings.themeMode == ThemeMode.DARK) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    label = "外观",
                    labelModifier = rowModifier("appearance"),
                    value = when (state.settings.themeMode) {
                        ThemeMode.SYSTEM -> "系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { onOpen(SettingsPage.APPEARANCE) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.TextFields,
                    label = "字体大小",
                    labelModifier = rowModifier("font"),
                    value = when (state.settings.fontSize) {
                        FontSize.SMALL -> "小"
                        FontSize.NORMAL -> "标准"
                        FontSize.LARGE -> "大"
                        FontSize.XLARGE -> "特大"
                    },
                    onClick = { onOpen(SettingsPage.FONT_SIZE) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    label = "个性化",
                    labelModifier = rowModifier("personalization"),
                    onClick = { onOpen(SettingsPage.PERSONALIZATION) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Translate,
                    label = "位置信息",
                    value = state.settings.location ?: "未设置",
                    labelModifier = rowModifier("location"),
                    onClick = { onOpen(SettingsPage.LOCATION) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    label = "自动折叠思考内容",
                    value = if (state.settings.autoCollapseThinking) "已开启" else "已关闭",
                    labelModifier = rowModifier("auto_collapse"),
                    onClick = { vm.setAutoCollapseThinking(!state.settings.autoCollapseThinking) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Vibration,
                    label = "触感反馈",
                    value = if (state.settings.hapticEnabled) {
                        "${state.settings.hapticMode.label} · ${com.wongiii.chat.domain.haptics.HapticLevels.label(state.settings.hapticLevel)}"
                    } else {
                        "已关闭"
                    },
                    labelModifier = rowModifier("haptic"),
                    onClick = { onOpen(SettingsPage.HAPTIC) },
                )
            }
            SectionLabel("服务")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Cloud,
                    label = "接口与模型",
                    labelModifier = rowModifier("services"),
                    value = state.settings.model,
                    onClick = { onOpen(SettingsPage.SERVICES) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Cloud,
                    label = "网络设置",
                    labelModifier = rowModifier("network"),
                    value = if (state.settings.network.llmApiKey.isNullOrBlank()) "未配置" else "已配置",
                    onClick = { onOpen(SettingsPage.NETWORK) },
                )
            }
            SectionLabel("语音")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Translate,
                    label = "主要语言",
                    labelModifier = rowModifier("asr"),
                    value = when (state.settings.asrLanguage) {
                        "zh" -> "中文"
                        "en" -> "English"
                        "ja" -> "日本語"
                        "ko" -> "한국어"
                        else -> "跟随应用"
                    },
                    onClick = { onOpen(SettingsPage.ASR_LANGUAGE) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Rounded.VolumeUp,
                    label = "朗读音色",
                    labelModifier = rowModifier("tts"),
                    value = if (state.settings.ttsVoice == TtsVoice.STELLA) "Stella（女声）" else "Tide（男声）",
                    onClick = { onOpen(SettingsPage.TTS) },
                )
            }
            Text(
                text = "朗读与语音输入使用你配置的语音服务；语音输入需要麦克风权限。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = colors.textTertiary,
                style = chatTypography().caption,
            )
            SectionLabel("关于")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    label = "检查更新",
                    labelModifier = rowModifier("update"),
                    value = Brand.VERSION,
                    onClick = { onOpen(SettingsPage.UPDATE) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    label = "服务协议",
                    labelModifier = rowModifier("terms"),
                    onClick = { onOpen(SettingsPage.TERMS) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.HelpOutline,
                    label = "帮助与反馈",
                    labelModifier = rowModifier("help"),
                    onClick = { onOpen(SettingsPage.HELP) },
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Code,
                    label = "开源许可",
                    labelModifier = rowModifier("licenses"),
                    onClick = { onOpen(SettingsPage.LICENSES) },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "${Brand.NAME} ${Brand.VERSION}",
                modifier = Modifier.fillMaxWidth(),
                color = colors.textCaption,
                style = chatTypography().caption,
                textAlign = TextAlign.Center,
            )
            if (state.cloudUser != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SecondaryButton("退出云端账号", onClick = onLeaveCloud, danger = true)
                }
            }
        }
    }
}

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit, titleModifier: Modifier = Modifier) {
    val colors = chatColors()
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(if (colors.dark) Color(0xFF353638) else Color(0xFFEAECEE))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            modifier = titleModifier.align(Alignment.Center),
            color = colors.textPrimary,
            style = chatTypography().title.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun SettingsDetail(
    page: SettingsPage,
    state: UiState,
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onOpenAuth: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
    onPickBackground: () -> Unit,
    headerModifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.settingsPage)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SettingsTopBar(title = settingsTitle(page), onBack = onBack, titleModifier = headerModifier)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp),
        ) {
            when (page) {
                SettingsPage.ACCOUNT -> AccountPage(state, onOpenAuth, vm)
                SettingsPage.DATA -> DataPage(state, onExport, onImport, onDeleteAll)
                SettingsPage.LANGUAGE -> LanguagePage(state, vm)
                SettingsPage.APPEARANCE -> AppearancePage(state, vm)
                SettingsPage.FONT_SIZE -> FontSizePage(state, vm)
                SettingsPage.PERSONALIZATION -> PersonalizationPage(state, vm, onPickBackground)
                SettingsPage.HAPTIC -> HapticPage(state, vm)
                SettingsPage.LOCATION -> LocationPage(state, vm)
                SettingsPage.SERVICES -> ServicesPage(state, vm, onOpen)
                SettingsPage.MODEL -> ModelPage(state, vm)
                SettingsPage.NETWORK -> NetworkPage(state, vm)
                SettingsPage.ASR_LANGUAGE -> AsrLanguagePage(state, vm)
                SettingsPage.TTS -> TtsPage(state, vm)
                SettingsPage.UPDATE -> UpdatePage(state, vm)
                SettingsPage.TERMS -> StaticTextPage(
                    "服务协议",
                    "本应用是本地优先的 AI 对话客户端。\n\n" +
                        "1. 你可以选择只在本机保存会话，也可以连接自己的服务器同步数据。\n" +
                        "2. 模型服务由你配置的第三方接口提供，生成内容的准确性、合规性和可用性由对应服务负责。\n" +
                        "3. API Key、服务器 Cookie 等敏感信息保存在本机加密存储中，不会上传到其他服务。\n" +
                        "4. 请勿使用本应用生成违法、侵权或危害他人的内容。\n" +
                        "5. 本应用按现状提供，不对模型输出作任何明示或暗示的保证。",
                )
                SettingsPage.LICENSES -> StaticTextPage(
                    "开源许可",
                    openSourceLicensesText(),
                )
                SettingsPage.HELP -> StaticTextPage(
                    "帮助与反馈",
                    "常见问题：\n\n" +
                        "· 如何填写接口地址？在“服务 → 网络设置”中填写完整的 LLM、搜索和语音 Base URL，以及对应 API Key。\n" +
                        "· 为什么语音输入不可用？请允许麦克风权限，并确认语音服务地址可访问。\n" +
                        "· 如何切换本地和云端？在“账户 → 账号管理”中连接服务器并登录；默认数据保存在本机。\n" +
                        "· 附件如何工作？图片会压缩后以 base64 发送；PDF、Word、PPT、Excel 和文本会在本机提取文字后随消息发送，不再依赖文件上传接口。\n" +
                        "· 如何备份？在“数据管理”中导出 ZIP，格式与网页版兼容。\n\n" +
                        "如需反馈问题，请记录版本号、操作步骤和错误提示。",
                )
                else -> Unit
            }
        }
    }
}

private fun settingsTitle(page: SettingsPage): String = when (page) {
    SettingsPage.ACCOUNT -> "账号管理"
    SettingsPage.DATA -> "数据管理"
    SettingsPage.LANGUAGE -> "语言"
    SettingsPage.APPEARANCE -> "外观"
    SettingsPage.FONT_SIZE -> "字体大小"
    SettingsPage.PERSONALIZATION -> "个性化"
    SettingsPage.HAPTIC -> "触感反馈"
    SettingsPage.LOCATION -> "位置信息"
    SettingsPage.SERVICES -> "接口与模型"
    SettingsPage.MODEL -> "模型"
    SettingsPage.NETWORK -> "网络设置"
    SettingsPage.ASR_LANGUAGE -> "主要语言"
    SettingsPage.TTS -> "朗读"
    SettingsPage.UPDATE -> "检查更新"
    SettingsPage.TERMS -> "服务协议"
    SettingsPage.HELP -> "帮助与反馈"
    SettingsPage.LICENSES -> "开源许可"
    SettingsPage.ROOT -> "设置"
}

@Composable
private fun AccountPage(state: UiState, onOpenAuth: () -> Unit, vm: ChatViewModel) {
    val colors = chatColors()
    SectionLabel("当前模式")
    SettingsCard {
        SettingsRow(
            icon = if (state.mode == DataMode.CLOUD) Icons.Outlined.Cloud else Icons.Outlined.Storage,
            label = "数据位置",
            value = if (state.mode == DataMode.CLOUD) "云端账号" else "本机",
        )
        if (state.serverUrl != null) {
            SettingsRow(
                icon = Icons.Outlined.Cloud,
                label = "服务器",
                value = state.serverUrl,
            )
        }
        SettingsRow(
            icon = Icons.Outlined.Person,
            label = "账号",
            value = state.cloudUser ?: "未登录",
        )
    }
    Spacer(Modifier.height(18.dp))
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.cloudUser == null) {
            Text(
                "连接服务器后可以登录账号、把会话保存到云端，并在多台设备间同步。",
                style = chatTypography().caption,
                color = colors.textTertiary,
            )
            PrimaryButton("连接服务器", onClick = onOpenAuth, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "已登录 @${state.cloudUser}。退出后会回到本机模式，本机会话仍然保留。",
                style = chatTypography().caption,
                color = colors.textTertiary,
            )
            SecondaryButton("退出云端账号", onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth(), danger = true)
        }
    }
}

@Composable
private fun DataPage(
    state: UiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val colors = chatColors()
    SectionLabel("会话数据")
    SettingsCard {
        SettingsRow(
            icon = Icons.Outlined.Storage,
            label = "当前会话数",
            value = "${state.sessions.size}",
        )
        SettingsRow(
            icon = Icons.Outlined.Cloud,
            label = "数据位置",
            value = if (state.mode == DataMode.CLOUD) "云端" else "本机",
        )
    }
    Spacer(Modifier.height(18.dp))
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("导出全部会话（ZIP）", onClick = onExport, modifier = Modifier.fillMaxWidth())
        SecondaryButton("导入历史 ZIP", onClick = onImport, modifier = Modifier.fillMaxWidth())
        SecondaryButton("清空当前数据", onClick = onDeleteAll, modifier = Modifier.fillMaxWidth(), danger = true)
        Text(
            "导出格式与网页版兼容，包含会话 JSON 和图片原件；导入会替换当前数据，请先备份。",
            style = chatTypography().caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun LanguagePage(state: UiState, vm: ChatViewModel) {
    SectionLabel("界面语言")
    SettingsCard {
        ChoiceRow("中文（简体）", state.settings.locale == "zh-CN") { vm.setLocale("zh-CN") }
        ChoiceRow("English", state.settings.locale == "en-US") { vm.setLocale("en-US") }
    }
    Text(
        "界面语言会立即保存；部分系统文案和模型输出不受影响。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = chatTypography().caption,
        color = chatColors().textTertiary,
    )
}

@Composable
private fun AppearancePage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    SectionLabel("外观")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            ThemeMode.SYSTEM to "系统",
            ThemeMode.LIGHT to "浅色",
            ThemeMode.DARK to "深色",
        ).forEach { (mode, label) ->
            val selected = state.settings.themeMode == mode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .clip(RoundedCornerShape(CardRadius))
                    .clickable { vm.setTheme(mode) },
                color = if (selected) colors.accentSoft else colors.card,
                shape = RoundedCornerShape(CardRadius),
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 1.5.dp else 0.5.dp,
                    if (selected) colors.accent else colors.border,
                ),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        when (mode) {
                            ThemeMode.SYSTEM -> Icons.Outlined.LightMode
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        },
                        null,
                        tint = if (selected) colors.accent else colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(label, style = chatTypography().label, color = colors.textPrimary)
                }
            }
        }
    }
    Text(
        "选择“系统”时会跟随手机的深色模式。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )
}

@Composable
private fun FontSizePage(state: UiState, vm: ChatViewModel) {
    SectionLabel("字体大小")
    SettingsCard {
        FontSize.entries.forEach { size ->
            ChoiceRow(
                label = when (size) {
                    FontSize.SMALL -> "小"
                    FontSize.NORMAL -> "标准"
                    FontSize.LARGE -> "大"
                    FontSize.XLARGE -> "特大"
                },
                selected = state.settings.fontSize == size,
                onClick = { vm.setFontSize(size) },
            )
        }
    }
    Text(
        "示例：今天有什么可以帮到你？",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().body,
        color = chatColors().textPrimary,
    )
}

@Composable
private fun PersonalizationPage(
    state: UiState,
    vm: ChatViewModel,
    onPickBackground: () -> Unit,
) {
    val colors = chatColors()
    SectionLabel("主题色")
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.compose)
                        .border(
                            if (state.settings.accent == accent) 3.dp else 0.dp,
                            if (state.settings.accent == accent) colors.card else Color.Transparent,
                            CircleShape,
                        )
                        .clickable { vm.setAccent(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.settings.accent == accent) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
    SectionLabel("对话气泡")
    SettingsCard {
        listOf(
            BubbleStyle.TINTED to "主题色浅底",
            BubbleStyle.NEUTRAL to "中性灰",
            BubbleStyle.OUTLINED to "描边",
        ).forEach { (style, label) ->
            ChoiceRow(label, state.settings.bubbleStyle == style) { vm.setBubble(style) }
        }
    }
    SectionLabel("聊天背景")
    SettingsCard {
        ChoiceRow("纯色", state.settings.backgroundStyle == BackgroundStyle.PLAIN) {
            vm.setBackground(BackgroundStyle.PLAIN)
        }
        ChoiceRow("主题色渐变", state.settings.backgroundStyle == BackgroundStyle.GRADIENT) {
            vm.setBackground(BackgroundStyle.GRADIENT)
        }
        ChoiceRow("自定义图片", state.settings.backgroundStyle == BackgroundStyle.CUSTOM) {
            onPickBackground()
        }
        if (state.settings.backgroundStyle == BackgroundStyle.CUSTOM) {
            TextButton(onClick = { vm.setBackgroundImage(null) }) {
                Text("移除自定义背景", color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun LocationPage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    val parts = state.settings.location.orEmpty().split(" ", limit = 2)
    var country by rememberSaveable { mutableStateOf(parts.getOrNull(0).orEmpty()) }
    var city by rememberSaveable { mutableStateOf(parts.getOrNull(1).orEmpty()) }
    Text(
        "位置信息会加入每次请求的 latest reminder，帮助模型理解时区和本地语境。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("国家/地区", style = chatTypography().caption, color = colors.textSecondary)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = country,
            onValueChange = { country = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：中国", style = chatTypography().caption, color = colors.textCaption) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("城市/省份", style = chatTypography().caption, color = colors.textSecondary)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：上海", style = chatTypography().caption, color = colors.textCaption) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("清除位置", onClick = {
            country = ""
            city = ""
            vm.setLocation(null)
            vm.toast("位置信息已清除")
        }, modifier = Modifier.weight(1f))
        PrimaryButton("保存", onClick = {
            val value = listOf(country.trim(), city.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            vm.setLocation(value.ifBlank { null })
            vm.toast(if (value.isBlank()) "位置信息已清除" else "位置信息已保存")
        }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ServicesPage(state: UiState, vm: ChatViewModel, onOpen: (SettingsPage) -> Unit) {
    val colors = chatColors()
    SectionLabel("模型")
    SettingsCard {
        SettingsRow(
            icon = Icons.Outlined.Cloud,
            label = "当前模型",
            value = state.settings.model,
            onClick = { onOpen(SettingsPage.MODEL) },
            showDivider = true,
        )
        SettingsRow(
            icon = Icons.Outlined.Cloud,
            label = "模型列表",
            value = "${state.settings.models.size} 个",
            onClick = { onOpen(SettingsPage.MODEL) },
        )
    }
    SectionLabel("接口")
    SettingsCard {
        SettingsRow(
            icon = Icons.Outlined.Cloud,
            label = "网络设置",
            value = if (state.settings.network.llmApiKey.isNullOrBlank()) "未配置" else "已配置",
            onClick = { onOpen(SettingsPage.NETWORK) },
        )
    }
    Text(
        "支持任意 Anthropic 兼容 Messages 接口。搜索与语音地址留空时使用对应默认地址；Key 留空时回退到 LLM Key。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )
    Column(Modifier.padding(horizontal = 20.dp)) {
        SecondaryButton(
            "获取模型列表",
            onClick = { vm.fetchModels() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelPage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    var custom by rememberSaveable { mutableStateOf("") }
    SectionLabel("当前模型")
    SettingsCard {
        state.settings.models.forEach { model ->
            ChoiceRow(model, state.settings.model == model) { vm.setModel(model) }
        }
    }
    SectionLabel("添加自定义模型")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("模型 ID") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        SecondaryButton("添加", onClick = {
            vm.addModel(custom)
            custom = ""
        })
    }
    Spacer(Modifier.height(14.dp))
    Column(Modifier.padding(horizontal = 20.dp)) {
        SecondaryButton("从接口获取模型", onClick = { vm.fetchModels() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "获取模型会调用当前 LLM 地址的 /models 接口。",
            style = chatTypography().caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun NetworkPage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    var llmUrl by rememberSaveable { mutableStateOf(state.settings.network.llmBaseUrl.orEmpty()) }
    var llmKey by rememberSaveable { mutableStateOf(state.settings.network.llmApiKey.orEmpty()) }
    var searchUrl by rememberSaveable { mutableStateOf(state.settings.network.searchBaseUrl.orEmpty()) }
    var searchKey by rememberSaveable { mutableStateOf(state.settings.network.searchApiKey.orEmpty()) }
    var speechUrl by rememberSaveable { mutableStateOf(state.settings.network.speechBaseUrl.orEmpty()) }
    var speechKey by rememberSaveable { mutableStateOf(state.settings.network.speechApiKey.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = listOf(llmUrl, searchUrl, speechUrl).any { it.isNotBlank() && normalizeBaseUrl(it) == null }

    Text(
        "填写完整的路径前缀。LLM 默认 https://api.deepseek.com/anthropic/v1；搜索/语音默认 https://api.deepseek.com/v1/tools。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )
    NetworkField("LLM 地址", llmUrl, { llmUrl = it }, placeholder = "https://…/anthropic/v1")
    NetworkSecret("LLM API Key", llmKey, { llmKey = it })
    NetworkField("搜索地址", searchUrl, { searchUrl = it }, placeholder = "https://…/v1/tools")
    NetworkSecret("搜索 API Key（可留空）", searchKey, { searchKey = it })
    NetworkField("语音地址", speechUrl, { speechUrl = it }, placeholder = "https://…/v1/tools")
    NetworkSecret("语音 API Key（可留空）", speechKey, { speechKey = it })
    if (error != null) {
        Text(
            error.orEmpty(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = colors.danger,
            style = chatTypography().caption,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("重置", onClick = {
            llmUrl = ""
            llmKey = ""
            searchUrl = ""
            searchKey = ""
            speechUrl = ""
            speechKey = ""
            error = null
            vm.setNetwork(NetworkSettings())
        }, modifier = Modifier.weight(1f))
        PrimaryButton("保存", onClick = {
            if (invalid) {
                error = "地址必须是有效的 http(s) URL，且不能包含查询串或锚点"
            } else {
                error = null
                vm.setNetwork(
                    NetworkSettings(
                        llmBaseUrl = normalizeBaseUrl(llmUrl),
                        searchBaseUrl = normalizeBaseUrl(searchUrl),
                        speechBaseUrl = normalizeBaseUrl(speechUrl),
                        llmApiKey = llmKey.trim().ifBlank { null },
                        searchApiKey = searchKey.trim().ifBlank { null },
                        speechApiKey = speechKey.trim().ifBlank { null },
                    )
                )
                vm.toast("网络设置已保存")
            }
        }, modifier = Modifier.weight(1f), enabled = !invalid)
    }
    Text(
        "API Key 会保存在本机加密存储中；连接云端账号时按账号保存在服务器加密存储中。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )
}

@Composable
private fun NetworkField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
) {
    NetworkFieldBase(label, value, onChange, placeholder, secret = false)
}

@Composable
private fun NetworkSecret(label: String, value: String, onChange: (String) -> Unit) {
    NetworkFieldBase(label, value, onChange, "sk-…", secret = true)
}

@Composable
private fun NetworkFieldBase(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = chatTypography().caption, color = chatColors().textSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = chatColors().textCaption, style = chatTypography().caption) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
            ),
        )
    }
}

@Composable
private fun AsrLanguagePage(state: UiState, vm: ChatViewModel) {
    SectionLabel("语音输入语言")
    SettingsCard {
        listOf(
            "auto" to "跟随应用",
            "zh" to "中文",
            "en" to "English",
            "ja" to "日本語",
            "ko" to "한국어",
        ).forEach { (code, label) ->
            ChoiceRow(label, state.settings.asrLanguage == code) { vm.setAsrLanguage(code) }
        }
    }
    Text(
        "Whisper 会按选择的语言提示识别；选择“跟随应用”时使用界面语言。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = chatColors().textTertiary,
    )
}

@Composable
private fun TtsPage(state: UiState, vm: ChatViewModel) {
    SectionLabel("朗读音色")
    SettingsCard {
        ChoiceRow("Stella（女声）", state.settings.ttsVoice == TtsVoice.STELLA) { vm.setTtsVoice(TtsVoice.STELLA) }
        ChoiceRow("Tide（男声）", state.settings.ttsVoice == TtsVoice.TIDE) { vm.setTtsVoice(TtsVoice.TIDE) }
    }
    SectionLabel("朗读情绪")
    SettingsCard {
        TtsEmotion.entries.forEach { emotion ->
            ChoiceRow(emotion.label, state.settings.ttsEmotion == emotion) { vm.setTtsEmotion(emotion) }
        }
    }
    Text(
        "朗读会调用语音服务；音色与情绪在设备端由对应 TTS 引擎解释。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = chatTypography().caption,
        color = chatColors().textTertiary,
    )
}

@Composable
private fun HapticPage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    val context = LocalContext.current
    val haptics = remember(context) { StreamingHaptics(context) }
    val scope = rememberCoroutineScope()
    var previewing by remember { mutableStateOf(false) }

    DisposableEffect(haptics) {
        onDispose { haptics.stop() }
    }

    SectionLabel("流式输出")
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setHapticEnabled(!state.settings.hapticEnabled) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "流式输出震动",
                    style = chatTypography().body,
                    color = colors.textPrimary,
                )
                Text(
                    if (haptics.available) {
                        "思考与回答逐字出现时，跟手给出连续触感"
                    } else {
                        "当前设备没有可用的震动马达"
                    },
                    modifier = Modifier.padding(top = 3.dp),
                    style = chatTypography().caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.settings.hapticEnabled,
                onCheckedChange = { vm.setHapticEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.border,
                ),
            )
        }
    }
    Text(
        "震动强度、节奏和纹理都会随文字到达的密度变化；输出暂停后会自然收敛，不会一直持续震动。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )

    SectionLabel("震动模式")
    SettingsCard {
        HapticMode.entries.forEachIndexed { index, mode ->
            HapticModeRow(
                mode = mode,
                selected = state.settings.hapticMode == mode,
                onClick = { vm.setHapticMode(mode) },
            )
            if (index != HapticMode.entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = colors.divider,
                )
            }
        }
    }

    SectionLabel("震动强度")
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (HapticLevels.MIN..HapticLevels.MAX).forEach { level ->
                    val selected = state.settings.hapticLevel == level
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable { vm.setHapticLevel(level) },
                        color = if (selected) colors.accentSoft else colors.settingsPage,
                        shape = RoundedCornerShape(11.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 1.dp else 0.5.dp,
                            if (selected) colors.accent else colors.border,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$level",
                                style = chatTypography().label.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (selected) colors.accent else colors.textSecondary,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "第 ${state.settings.hapticLevel} 挡",
                    style = chatTypography().caption,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    HapticLevels.label(state.settings.hapticLevel),
                    style = chatTypography().caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
    Text(
        "1 挡最轻，5 挡最明显。水流模式在同等挡位下会比涟漪、心跳更柔和。",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = chatTypography().caption,
        color = colors.textTertiary,
    )

    SectionLabel("试听")
    Column(Modifier.padding(horizontal = 16.dp)) {
        SecondaryButton(
            text = if (previewing) "正在试听…" else "试听当前触感（约 2 秒）",
            onClick = {
                if (!previewing && haptics.available) {
                    previewing = true
                    haptics.preview(scope, state.settings.hapticMode, state.settings.hapticLevel)
                    scope.launch {
                        delay(2900)
                        previewing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = haptics.available && !previewing,
        )
        Text(
            if (haptics.fineControl) {
                "你的设备支持振幅控制，挡位会调节真实震动力度。不同机型的马达特性会让触感略有差异。"
            } else {
                "你的设备不支持振幅控制，触感强弱会通过脉冲长短和疏密来表现。"
            },
            modifier = Modifier.padding(top = 10.dp),
            style = chatTypography().caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun HapticModeRow(mode: HapticMode, selected: Boolean, onClick: () -> Unit) {
    val colors = chatColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HapticGlyph(mode, if (selected) colors.accent else colors.textTertiary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                mode.label,
                style = chatTypography().body,
                color = colors.textPrimary,
            )
            Text(
                mode.description,
                modifier = Modifier.padding(top = 2.dp),
                style = chatTypography().caption,
                color = colors.textTertiary,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HapticGlyph(mode: HapticMode, color: Color) {
    Canvas(Modifier.size(width = 42.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height
        val cy = h / 2
        val stroke = 1.6.dp.toPx()
        when (mode) {
            HapticMode.FLOW -> {
                val path = Path()
                var x = 0f
                while (x <= w) {
                    val envelope = 0.55f + 0.45f * kotlin.math.sin(x / w * PI.toFloat() * 2f)
                    val y = cy + kotlin.math.sin(x / w * PI.toFloat() * 7f) * h * 0.27f * envelope
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 1.5f
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            HapticMode.RIPPLE -> {
                val path = Path()
                path.moveTo(0f, cy + h * 0.18f)
                path.quadraticBezierTo(w * 0.18f, cy - h * 0.36f, w * 0.36f, cy + h * 0.18f)
                path.quadraticBezierTo(w * 0.54f, cy - h * 0.30f, w * 0.72f, cy + h * 0.18f)
                path.quadraticBezierTo(w * 0.86f, cy - h * 0.22f, w, cy + h * 0.18f)
                drawPath(path, color, style = Stroke(stroke))
            }
            HapticMode.BREATH -> {
                val path = Path()
                path.moveTo(0f, cy + h * 0.12f)
                path.cubicTo(
                    w * 0.24f, cy + h * 0.12f,
                    w * 0.27f, cy - h * 0.38f,
                    w * 0.5f, cy - h * 0.38f,
                )
                path.cubicTo(
                    w * 0.73f, cy - h * 0.38f,
                    w * 0.76f, cy + h * 0.12f,
                    w, cy + h * 0.12f,
                )
                drawPath(path, color, style = Stroke(stroke))
            }
            HapticMode.HEARTBEAT -> {
                val path = Path()
                path.moveTo(0f, cy)
                path.lineTo(w * 0.22f, cy)
                path.lineTo(w * 0.30f, cy - h * 0.40f)
                path.lineTo(w * 0.38f, cy + h * 0.30f)
                path.lineTo(w * 0.46f, cy)
                path.lineTo(w * 0.58f, cy)
                path.lineTo(w * 0.64f, cy - h * 0.24f)
                path.lineTo(w * 0.70f, cy + h * 0.16f)
                path.lineTo(w * 0.76f, cy)
                path.lineTo(w, cy)
                drawPath(path, color, style = Stroke(stroke))
            }
            HapticMode.TYPEWRITER -> {
                for (i in 0..5) {
                    val x = w * (0.1f + 0.16f * i)
                    val height = if (i % 2 == 0) h * 0.52f else h * 0.30f
                    drawLine(
                        color,
                        Offset(x, cy - height / 2),
                        Offset(x, cy + height / 2),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            HapticMode.RAIN -> {
                val drops = listOf(
                    0.12f to 0.34f,
                    0.32f to 0.70f,
                    0.50f to 0.44f,
                    0.70f to 0.76f,
                    0.88f to 0.38f,
                )
                drops.forEach { (fx, fy) ->
                    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(w * fx, h * fy))
                    drawLine(
                        color.copy(alpha = 0.55f),
                        Offset(w * fx, h * fy - 5.dp.toPx()),
                        Offset(w * fx, h * fy - 2.dp.toPx()),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatePage(state: UiState, vm: ChatViewModel) {
    val colors = chatColors()
    val uriHandler = LocalUriHandler.current
    SectionLabel("版本")
    SettingsCard {
        SettingsRow(icon = Icons.Outlined.Info, label = "当前版本", value = Brand.VERSION)
        SettingsRow(
            icon = Icons.Rounded.Refresh,
            label = "检查更新",
            value = state.updateStatus,
            onClick = { vm.checkUpdate() },
        )
    }
    if (state.versionInfo != null && state.versionInfo.version != Brand.VERSION) {
        Column(Modifier.padding(20.dp)) {
            Text("新版本 ${state.versionInfo.version}", style = chatTypography().title, color = colors.textPrimary)
            if (state.versionInfo.notes.isNotBlank()) {
                Text(
                    state.versionInfo.notes,
                    modifier = Modifier.padding(top = 8.dp),
                    style = chatTypography().body,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("打开下载页面", onClick = {
                if (state.versionInfo.url.startsWith("http")) uriHandler.openUri(state.versionInfo.url)
            })
        }
    }
}

private fun openSourceLicensesText(): String = """
本应用包含或参考了以下开源项目。各项目的版权归原作者所有，并按各自许可证提供；完整许可证文本随源码分发。

界面与图标
· chat-demo — MIT License — Copyright (c) 2026 DeepSeek
  https://github.com/deepseek-ai/chat-demo
  本应用的布局、交互和部分 SVG 图标路径参考自该项目。

Android 与 Kotlin
· AndroidX、Jetpack Compose、Material 3、Material Icons — Apache License 2.0
· Kotlin、kotlinx.coroutines、kotlinx.serialization — Apache License 2.0

网络、存储与渲染
· OkHttp、Okio — Apache License 2.0
· Coil — Apache License 2.0
· commonmark-java — BSD 2-Clause License
· PDFBox-Android — Apache License 2.0
· AndroidX ExifInterface — Apache License 2.0

语音与模型工具
· CosyVoice — Apache License 2.0（FunAudioLLM / Alibaba）
· GPT-SoVITS — MIT License（RVC-Boss）
· Whisper — MIT License（OpenAI）
· Node.js — MIT License（可选的自建服务端）

MIT License 主要条款
允许自由使用、复制、修改、合并、发布、分发、再许可和销售本项目及其副本，但必须在所有副本或重要部分中保留原版权声明和本许可声明。软件按“现状”提供，不附带任何明示或暗示担保。

商标说明
DeepSeek 名称、鲸鱼标识及其他官方品牌标识不包含在上述 MIT 许可中，商标权仍归其权利人所有。本应用使用中性的 Chat 品牌，不代表与 DeepSeek 官方存在关联或获得其背书。
""".trimIndent()

@Composable
private fun StaticTextPage(title: String, content: String) {
    Text(
        text = content,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        style = chatTypography().body,
        color = chatColors().textSecondary,
    )
}

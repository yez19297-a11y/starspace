package com.starspace.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starspace.app.data.AppSettings
import com.starspace.app.data.LLMConfig
import com.starspace.app.data.ModelCatalog
import com.starspace.app.data.UserAccount

@Composable
fun SettingsScreen(
    user: UserAccount?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleTabletMode: (Boolean) -> Unit,
    onSetDarkMode: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onOpenLLMConfig: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit
) {
    var serverUrl by remember(settings.serverUrl, settings.serverConfigured) {
        mutableStateOf(if (settings.serverConfigured) settings.serverUrl else "")
    }
    val theme = AppTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp)
    ) {
        SettingsHeader(title = "设置", onBack = onBack)

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("平板模式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                    Text(
                        "在宽屏设备上使用左右分栏，类似平板即时通讯布局",
                        color = theme.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = settings.tabletMode,
                    onCheckedChange = onToggleTabletMode
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("深色模式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                    Text(
                        when (settings.darkMode) {
                            "dark" -> "始终使用深色模式"
                            "light" -> "始终使用浅色模式"
                            else -> "跟随系统自动切换"
                        },
                        color = theme.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row {
                    DarkModeOption("自动", settings.darkMode == "system", "system", onSetDarkMode)
                    Spacer(Modifier.width(6.dp))
                    DarkModeOption("浅色", settings.darkMode == "light", "light", onSetDarkMode)
                    Spacer(Modifier.width(6.dp))
                    DarkModeOption("深色", settings.darkMode == "dark", "dark", onSetDarkMode)
                }
            }
        }

        Text(
            "服务连接",
            color = theme.ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp)
        )
        SettingCard {
            Column {
                Text(
                    if (settings.backendReady) "已连接：${settings.serverUrl}"
                    else "未配置后端，空间与评论只保存在本机（直连模型仍可正常聊天）",
                    color = theme.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("后端地址") },
                    placeholder = { Text("例如 http://192.168.1.10:8000") },
                    supportingText = { Text("留空即纯本地模式；模拟器访问本机用 10.0.2.2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = { onSaveServerUrl(serverUrl) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
                    ) { Text(if (serverUrl.isBlank()) "使用本地模式" else "保存连接") }
                    if (settings.backendReady) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onSaveServerUrl("") }) { Text("断开") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingCard {
            val llm = settings.activeConfig
            val count = settings.llmConfigs.size
            SettingRow(
                title = "LLM 服务",
                detail = when {
                    count == 0 -> "还没有配置，点此添加第一套服务"
                    else -> "${llm.name} · ${llm.defaultModel} · ${if (llm.directMode) "直连" else "后端"} · 共 $count 套"
                },
                onClick = onOpenLLMConfig
            )
        }

        Text(
            "通用",
            color = AppTheme.colors.ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp)
        )
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("通知", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                    Text(
                        "AI 回复消息、评论你的动态或发布新动态时推送到通知栏",
                        color = theme.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onToggleNotifications
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingCard {
            SettingRow(
                title = "备份与恢复",
                detail = "导出配置或完整数据，换机时恢复",
                onClick = onOpenBackup
            )
            SettingDivider()
            SettingRow(
                title = "关于星屿",
                detail = "查看应用特性、版本和设计说明",
                onClick = onOpenAbout
            )
            SettingDivider()
            SettingRow(
                title = "退出登录",
                detail = user?.let { "当前账号：${it.accountName}" } ?: "未登录",
                onClick = onLogout,
                danger = true
            )
        }
    }
}

@Composable
private fun DarkModeOption(
    label: String,
    selected: Boolean,
    mode: String,
    onSetDarkMode: (String) -> Unit
) {
    val theme = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) theme.starBlue else theme.inputFieldBg,
        modifier = Modifier.clickable { onSetDarkMode(mode) }
    ) {
        Text(
            label,
            color = if (selected) Color.White else theme.muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun LLMConfigScreen(
    configs: List<LLMConfig>,
    activeConfigId: String,
    onBack: () -> Unit,
    onSave: (LLMConfig) -> Unit,
    onDelete: (String) -> Unit,
    onActivate: (LLMConfig) -> Unit,
    onTestDirect: (LLMConfig, (Result<String>) -> Unit) -> Unit,
    onTestBackend: ((Result<String>) -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf<LLMConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

    val current = editing
    if (current != null) {
        LLMConfigEditor(
            config = current,
            isNew = creating,
            onBack = { editing = null },
            onSave = { saved ->
                onSave(saved)
                editing = null
            },
            onDelete = {
                onDelete(current.id)
                editing = null
            },
            onTestDirect = onTestDirect,
            onTestBackend = onTestBackend
        )
    } else {
        LLMConfigList(
            configs = configs,
            activeConfigId = activeConfigId,
            onBack = onBack,
            onEdit = {
                creating = false
                editing = it
            },
            onCreate = {
                creating = true
                editing = LLMConfig(
                    name = "配置 ${configs.size + 1}",
                    directMode = true,
                    baseUrl = configs.lastOrNull()?.baseUrl ?: "https://api.deepseek.com/v1",
                    defaultModel = configs.lastOrNull()?.defaultModel ?: "deepseek-flash"
                )
            },
            onActivate = onActivate,
            onDelete = onDelete
        )
    }
}

@Composable
private fun LLMConfigList(
    configs: List<LLMConfig>,
    activeConfigId: String,
    onBack: () -> Unit,
    onEdit: (LLMConfig) -> Unit,
    onCreate: () -> Unit,
    onActivate: (LLMConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    val theme = AppTheme.colors
    var pendingDelete by remember { mutableStateOf<LLMConfig?>(null) }
    val active = configs.find { it.id == activeConfigId } ?: configs.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsHeader(title = "LLM 服务", onBack = onBack)
        Text(
            "可以保存多套配置，随时手动切换当前使用的一套",
            color = theme.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(12.dp))

        if (configs.isEmpty()) {
            SettingCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "还没有任何配置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.ink
                    )
                    Text(
                        "添加一套 OpenAI 兼容的接口地址、密钥和模型，就能开始对话。",
                        color = theme.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        configs.forEach { config ->
            val inUse = config.id == active?.id
            SettingCard {
                Column(Modifier.clickable { onEdit(config) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    config.name.ifBlank { "未命名配置" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.ink
                                )
                                if (inUse) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = theme.navItemSelectedBg
                                    ) {
                                        Text(
                                            "使用中",
                                            color = theme.starBlue,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "${config.defaultModel} · ${if (config.directMode) "直连" else "后端中转"}",
                                color = theme.muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                config.baseUrl,
                                color = theme.muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                        if (!inUse) {
                            TextButton(onClick = { onActivate(config) }) { Text("使用") }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEdit(config) },
                            modifier = Modifier.weight(1f)
                        ) { Text("编辑") }
                        OutlinedButton(
                            onClick = { pendingDelete = config },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.errorRed)
                        ) { Text("删除") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
        ) { Text("＋ 新建配置") }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) { Text("删除", color = theme.errorRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
            title = { Text("删除配置") },
            text = { Text("确定删除「${target.name}」吗？该操作不可撤销。") },
            containerColor = theme.cardBg
        )
    }
}

@Composable
private fun LLMConfigEditor(
    config: LLMConfig,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (LLMConfig) -> Unit,
    onDelete: () -> Unit,
    onTestDirect: (LLMConfig, (Result<String>) -> Unit) -> Unit,
    onTestBackend: ((Result<String>) -> Unit) -> Unit
) {
    var draft by remember(config.id) { mutableStateOf(config) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    val theme = AppTheme.colors

    fun normalized(): LLMConfig = draft.copy(
        name = draft.name.trim().ifBlank { "未命名配置" },
        apiKey = draft.apiKey.trim(),
        baseUrl = draft.baseUrl.trim().trimEnd('/'),
        defaultModel = draft.defaultModel.trim(),
        connected = false
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsHeader(title = if (isNew) "新建 LLM 配置" else "编辑 LLM 配置", onBack = onBack)

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("直连模型", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                    Text(
                        if (draft.directMode)
                            "应用直接调用 OpenAI 兼容接口，密钥只保存在本机"
                        else
                            "通过自己的后端中转调用，密钥会同步到服务器",
                        color = theme.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = draft.directMode,
                    onCheckedChange = {
                        draft = draft.copy(directMode = it)
                        testResult = null
                        testError = null
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        SettingCard {
            Column {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("配置名称") },
                    placeholder = { Text("例如：DeepSeek 直连") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("API 密钥") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    label = { Text("接口地址") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                ModelPickerField(
                    label = "模型",
                    value = draft.defaultModel,
                    options = (ModelCatalog.all + draft.defaultModel).distinct(),
                    onValueChange = { draft = draft.copy(defaultModel = it) }
                )
                Text(
                    "模型列表持续更新，也可以在列表顶部直接输入自定义模型名",
                    color = theme.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
        }

        if (testResult != null) {
            Spacer(Modifier.height(8.dp))
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = theme.successGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("连接测试通过", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                        Text(
                            testResult!!,
                            color = theme.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        if (testError != null) {
            Spacer(Modifier.height(8.dp))
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("!", color = theme.errorRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "连接失败",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.errorRed
                        )
                        Text(
                            testError!!,
                            color = theme.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (!draft.directMode) {
                            Text(
                                "后端模式请确认手机能访问设置里的「后端地址」，模拟器访问本机用 10.0.2.2",
                                color = theme.muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (testing) return@OutlinedButton
                    testing = true
                    testResult = null
                    testError = null
                    val callback: (Result<String>) -> Unit = { result ->
                        testing = false
                        result.onSuccess { testResult = it }
                            .onFailure { testError = it.message ?: "未知错误" }
                    }
                    if (draft.directMode) onTestDirect(normalized(), callback)
                    else onTestBackend(callback)
                },
                enabled = !testing,
                modifier = Modifier.weight(1f)
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = theme.starBlue
                    )
                } else {
                    Text("测试连接")
                }
            }
            Button(
                onClick = { onSave(normalized()) },
                enabled = !testing && draft.baseUrl.isNotBlank() && draft.defaultModel.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
            ) { Text("保存配置") }
        }

        if (!isNew) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = theme.errorRed)
            ) { Text("删除这套配置") }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val theme = AppTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsHeader(title = "关于星屿", onBack = onBack)

        SettingCard {
            Column(Modifier.padding(4.dp)) {
                Text("星屿 MVP", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = theme.ink)
                Text(
                    "一个独立的 AI 社交应用",
                    color = theme.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }

        Text(
            "应用特性",
            color = theme.ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp)
        )
        SettingCard {
            AboutFeature("独立提示词", "每个 AI 对话都可以拥有单独的人格、模型和随机度。")
            SettingDivider()
            AboutFeature("群聊", "可以把多个 AI 伙伴拉进同一个群；每人独立判断要不要开口，也能看到别人刚说的话接着聊，@ 谁谁必回。")
            SettingDivider()
            AboutFeature("独立空间", "动态、评论、点赞都保存在自己的应用与后端中；评论里 @ 角色会得到回复，AI 也会主动来你的动态下聊天。")
            SettingDivider()
            AboutFeature("不定时动态", "每个角色可按随机时间间隔自动发布自己的动态。")
            SettingDivider()
            AboutFeature("平板模式", "开启后在宽屏设备上使用左右分栏，更接近平板即时通讯体验。")
            SettingDivider()
            AboutFeature("本地身份", "用户名、账号名和密码仅保存在当前设备，用于本地身份识别。")
            SettingDivider()
            AboutFeature("密钥保护", "直连时密钥只保存在本机应用私有目录；后端模式下密钥只存在服务端。")
            SettingDivider()
            AboutFeature("LLM 服务", "可保存多套 OpenAI 兼容配置，手动切换当前使用的一套；模型从列表中选择，也支持自定义模型名。")
            Spacer(Modifier.height(14.dp))
            AboutFeature("系统通知", "AI 回复消息、评论你的动态或发布新动态时推送到通知栏；点通知可直接回到对应会话。")
            Spacer(Modifier.height(14.dp))
            AboutFeature("备份与恢复", "完整备份包含聊天与动态，也可以只导出配置；文件只保存在你选择的位置。")
        }

        Text(
            "版本 1.1.0 · Android 8.0 及以上",
            color = theme.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
        )
    }
}

@Composable
internal fun SettingsHeader(title: String, onBack: () -> Unit) {
    val theme = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = theme.ink)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = theme.ink)
    }
}

@Composable
internal fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.cardBg
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val theme = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) theme.errorRed else theme.ink
            )
            Text(detail, color = theme.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text("\u203A", color = theme.muted, fontSize = 20.sp)
    }
}

@Composable
private fun SettingDivider() {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.divider)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AboutFeature(title: String, detail: String) {
    val theme = AppTheme.colors
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
        Text(detail, color = theme.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

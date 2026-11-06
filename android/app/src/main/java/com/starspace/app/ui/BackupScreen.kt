package com.starspace.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    buildBackup: (Boolean) -> String,
    onRestore: (String) -> String,
    onNotify: (String) -> Unit
) {
    val theme = AppTheme.colors
    val context = LocalContext.current
    var pendingRaw by remember { mutableStateOf<String?>(null) }
    var pendingKind by remember { mutableStateOf("full") }
    var showConfirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            onNotify("读取失败，请确认文件是星屿导出的 JSON")
        } else {
            pendingRaw = raw
            pendingKind = runCatching {
                JSONObject(raw).optString("kind", "full")
            }.getOrDefault("full")
            showConfirm = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsHeader(title = "备份与恢复", onBack = onBack)

        Text(
            "备份文件保存在你选择的位置，不会上传到任何服务器。",
            color = theme.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(12.dp))
        BackupAction(
            icon = Icons.Outlined.FileDownload,
            title = "导出完整备份",
            detail = "账号、角色、群聊、聊天记录、动态和全部设置",
            onClick = {
                val name = "starspace-full-${timestamp()}.json"
                shareBackup(context, buildBackup(true), name, onNotify)
            }
        )
        Spacer(Modifier.height(10.dp))
        BackupAction(
            icon = Icons.Outlined.Tune,
            title = "只导出配置",
            detail = "角色、群组、LLM 服务与外观设置，不含聊天和动态",
            onClick = {
                val name = "starspace-config-${timestamp()}.json"
                shareBackup(context, buildBackup(false), name, onNotify)
            }
        )
        Spacer(Modifier.height(10.dp))
        BackupAction(
            icon = Icons.Outlined.FileUpload,
            title = "从文件恢复",
            detail = "选择之前导出的 .json 文件，覆盖当前数据",
            onClick = {
                picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        )

        Spacer(Modifier.height(18.dp))
        Surface(
            color = theme.cardBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("注意事项", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 备份里包含 LLM 的 API Key 和本机账号信息，请勿随意分享\n" +
                        "• 聊天记录只在完整备份里；恢复配置不会清空现有聊天\n" +
                        "• 恢复会覆盖同名的角色和群组，操作前建议先导出一次",
                    color = theme.muted,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showConfirm) {
        val raw = pendingRaw
        val isConfig = pendingKind == "config"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("恢复备份？") },
            text = {
                Text(
                    if (isConfig) {
                        "这是一份配置备份。将覆盖当前的角色、群组和设置，聊天记录与动态不受影响。"
                    } else {
                        "这是一份完整备份。将覆盖当前的角色、群组、设置、聊天记录和动态。此操作不可撤销。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        if (raw != null) {
                            val result = runCatching { onRestore(raw) }
                            onNotify(result.getOrElse { it.message ?: "恢复失败" })
                        }
                        pendingRaw = null
                    }
                ) { Text("恢复", color = theme.starBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false; pendingRaw = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackupAction(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    val theme = AppTheme.colors
    Surface(
        color = theme.cardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = theme.starBlue.copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = theme.starBlue,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
                Text(
                    detail,
                    fontSize = 11.sp,
                    color = theme.muted,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

private fun shareBackup(context: Context, content: String, fileName: String, onNotify: (String) -> Unit) {
    runCatching {
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "保存备份文件"))
    }.onFailure {
        onNotify("导出失败：${it.message ?: "未知错误"}")
    }
}

private fun timestamp(): String =
    SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())

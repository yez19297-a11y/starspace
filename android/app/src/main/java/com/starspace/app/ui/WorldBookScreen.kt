package com.starspace.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.starspace.app.data.WorldBook
import com.starspace.app.data.WorldBookEntry

/**
 * 世界书管理页。
 *
 * 条目是全局的，群聊里按关键词触发；单个群可以在群设置里关掉世界书。
 */
@Composable
fun WorldBookScreen(
    entries: List<WorldBookEntry>,
    onBack: () -> Unit,
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val theme = AppTheme.colors
    var editorTarget by remember { mutableStateOf<WorldBookEntry?>(null) }
    // 新建时也要有一个稳定的对象，否则每次重组都会生成新 id，输入框会被重置
    var creatingEntry by remember { mutableStateOf<WorldBookEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<WorldBookEntry?>(null) }
    val editingEntry = editorTarget ?: creatingEntry

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsHeader(title = "世界书", onBack = onBack)
        Text(
            "写几条世界观设定，群聊里最近的消息提到关键词时，对应设定会自动加进 AI 的提示词。",
            color = theme.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            SettingCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "还没有条目",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.ink
                    )
                    Text(
                        "比如标题「星屿市」，关键词「星屿市、港口」，内容是这座城市的样子。" +
                            "关键词留空则每条消息都会带上。",
                        color = theme.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        entries.forEach { entry ->
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.title.ifBlank { "未命名设定" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.ink
                            )
                            if (entry.keywords.isEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = theme.navItemSelectedBg
                                ) {
                                    Text(
                                        "常驻",
                                        color = theme.starBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            if (entry.keywords.isEmpty()) "每轮都会注入"
                            else "触发词：" + entry.keywords.joinToString("、"),
                            color = theme.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            entry.content,
                            color = theme.muted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { onToggle(entry.id, it) }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    TextButton(onClick = { editorTarget = entry }) { Text("编辑") }
                    TextButton(
                        onClick = { pendingDelete = entry },
                        colors = ButtonDefaults.textButtonColors(contentColor = theme.errorRed)
                    ) { Text("删除") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { creatingEntry = WorldBookEntry(title = "", content = "") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
        ) { Text("＋ 新建条目") }
    }

    editingEntry?.let { target ->
        WorldBookEditor(
            entry = target,
            isNew = editorTarget == null,
            onDismiss = {
                creatingEntry = null
                editorTarget = null
            },
            onSave = { saved ->
                onSave(saved)
                creatingEntry = null
                editorTarget = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.title.ifBlank { "未命名设定" }}」？") },
            text = { Text("删除后这条设定不会再注入群聊。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.errorRed)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WorldBookEditor(
    entry: WorldBookEntry,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (WorldBookEntry) -> Unit
) {
    val theme = AppTheme.colors
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var keywordText by remember(entry.id) { mutableStateOf(entry.keywords.joinToString("，")) }
    var content by remember(entry.id) { mutableStateOf(entry.content) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = theme.cardBg
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (isNew) "新建世界书条目" else "编辑世界书条目",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.ink
                )
                Text(
                    "AI 只会在关键词命中时看到这段设定，不会每次都塞进上下文。",
                    color = theme.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("星屿市") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("关键词（逗号分隔，留空则常驻）") },
                    placeholder = { Text("星屿市，港口，灯塔") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("设定内容") },
                    placeholder = { Text("故事发生在一座漂浮在海上的城市，终年有雾……") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            onSave(entry.copy(
                                title = title.trim(),
                                keywords = WorldBook.splitKeywords(keywordText),
                                content = content.trim()
                            ))
                        },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
                    ) { Text("保存") }
                }
            }
        }
    }
}

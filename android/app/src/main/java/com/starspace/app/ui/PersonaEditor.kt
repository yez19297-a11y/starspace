package com.starspace.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.starspace.app.data.Persona

@Composable
fun PersonaEditor(
    persona: Persona,
    isNew: Boolean,
    modelOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(persona.id) { mutableStateOf(persona.name) }
    var avatar by remember(persona.id) { mutableStateOf(persona.avatar) }
    var avatarImage by remember(persona.id) { mutableStateOf(persona.avatarImage) }
    var color by remember(persona.id) { mutableStateOf(persona.color) }
    var bio by remember(persona.id) { mutableStateOf(persona.bio) }
    var prompt by remember(persona.id) { mutableStateOf(persona.prompt) }
    var model by remember(persona.id) { mutableStateOf(persona.model) }
    var temperature by remember(persona.id) { mutableDoubleStateOf(persona.temperature) }
    var autoPost by remember(persona.id) { mutableStateOf(persona.autoPost) }
    var minHours by remember(persona.id) { mutableStateOf(persona.minPostHours.toString()) }
    var maxHours by remember(persona.id) { mutableStateOf(persona.maxPostHours.toString()) }

    val theme = AppTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = theme.cardBg
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (isNew) "认识一位新伙伴" else "角色与对话设置",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.ink
                )
                Text(
                    "提示词只影响当前对话与这个角色",
                    color = theme.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(18.dp))

                AvatarEditor(
                    title = "对话头像",
                    text = avatar,
                    color = color,
                    imagePath = avatarImage,
                    onTextChange = { avatar = it },
                    onColorChange = { color = it },
                    onImageChange = { avatarImage = it }
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("一句话签名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("独立系统提示词") },
                    supportingText = { Text("描述性格、说话方式、兴趣和边界") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7
                )
                Spacer(Modifier.height(12.dp))
                ModelPickerField(
                    label = "模型",
                    value = model,
                    options = (modelOptions + model).filter { it.isNotBlank() }.distinct(),
                    emptyLabel = "跟随当前 LLM 服务的默认模型",
                    onValueChange = { model = it }
                )
                Text(
                    "留空表示跟随「设置 → LLM 服务」里当前选中的模型",
                    color = theme.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
                Text(
                    "回复随机度  ${String.format("%.1f", temperature)}",
                    fontSize = 13.sp,
                    color = theme.ink
                )
                Slider(
                    value = temperature.toFloat(),
                    onValueChange = { temperature = it.toDouble() },
                    valueRange = 0f..1.5f
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = theme.divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("不定时发布动态", fontWeight = FontWeight.SemiBold, color = theme.ink)
                        Text("由后端调度，关闭应用也可执行", color = theme.muted, fontSize = 11.sp)
                    }
                    Switch(checked = autoPost, onCheckedChange = { autoPost = it })
                }
                if (autoPost) {
                    Row(Modifier.padding(top = 10.dp)) {
                        OutlinedTextField(
                            value = minHours,
                            onValueChange = { minHours = it.filter(Char::isDigit).take(3) },
                            label = { Text("最短间隔/时") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = maxHours,
                            onValueChange = { maxHours = it.filter(Char::isDigit).take(3) },
                            label = { Text("最长间隔/时") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = theme.errorRed)
                        ) { Text("删除") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val min = minHours.toIntOrNull()?.coerceIn(1, 168) ?: 4
                            val max = maxHours.toIntOrNull()?.coerceIn(min, 720) ?: 12
                            if (name.isNotBlank() && prompt.isNotBlank()) {
                                onSave(persona.copy(
                                    name = name.trim(),
                                    avatar = avatar.ifBlank { name.take(1) },
                                    avatarImage = avatarImage,
                                    color = color,
                                    bio = bio.trim(),
                                    prompt = prompt.trim(),
                                    model = model.trim(),
                                    temperature = temperature,
                                    autoPost = autoPost,
                                    minPostHours = min,
                                    maxPostHours = max
                                ))
                            }
                        },
                        enabled = name.isNotBlank() && prompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
                    ) { Text("保存") }
                }
            }
        }
    }
}

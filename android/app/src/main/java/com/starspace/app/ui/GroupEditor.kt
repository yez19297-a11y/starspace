package com.starspace.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.starspace.app.data.GroupChat
import com.starspace.app.data.Persona

@Composable
fun GroupEditor(
    group: GroupChat,
    personas: List<Persona>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (GroupChat) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var avatar by remember(group.id) { mutableStateOf(group.avatar) }
    var color by remember(group.id) { mutableStateOf(group.color) }
    var avatarImage by remember(group.id) { mutableStateOf(group.avatarImage) }
    var selectedMembers by remember(group.id) {
        mutableStateOf(group.members.toSet())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (isNew) "发起群聊" else "群聊设置",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "选择要拉进群里的 AI 伙伴",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("群聊名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                AvatarEditor(
                    title = "群头像",
                    text = avatar,
                    color = color,
                    imagePath = avatarImage,
                    onTextChange = { avatar = it },
                    onColorChange = { color = it },
                    onImageChange = { avatarImage = it }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFEEF1F5))
                Spacer(Modifier.height(12.dp))
                Text("群成员", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "至少选择 1 位成员",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                personas.forEach { persona ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMembers = if (persona.id in selectedMembers) {
                                    selectedMembers - persona.id
                                } else {
                                    selectedMembers + persona.id
                                }
                            }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            text = persona.avatar,
                            color = Color(persona.color),
                            size = 38,
                            imagePath = persona.avatarImage
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(persona.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(persona.bio, color = Muted, fontSize = 11.sp)
                        }
                        Checkbox(
                            checked = persona.id in selectedMembers,
                            onCheckedChange = { checked ->
                                selectedMembers = if (checked) {
                                    selectedMembers + persona.id
                                } else {
                                    selectedMembers - persona.id
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD85B64))
                        ) { Text("解散群聊") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && selectedMembers.isNotEmpty()) {
                                onSave(group.copy(
                                    name = name.trim(),
                                    avatar = avatar.ifBlank { name.take(1) },
                                    color = color,
                                    avatarImage = avatarImage,
                                    members = selectedMembers.toList()
                                ))
                            }
                        },
                        enabled = name.isNotBlank() && selectedMembers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = StarBlue)
                    ) { Text("保存") }
                }
            }
        }
    }
}

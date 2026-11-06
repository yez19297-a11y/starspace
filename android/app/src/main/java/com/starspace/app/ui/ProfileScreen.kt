package com.starspace.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.starspace.app.data.UserAccount

@Composable
fun ProfileScreen(
    user: UserAccount,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onUpdateUser: (UserAccount) -> Unit,
    onReset: () -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    val theme = AppTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp)
    ) {
        ScreenHeader("我的")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(24.dp),
            color = theme.cardBg
        ) {
            Row(Modifier.padding(20.dp)) {
                Avatar(
                    text = user.avatar,
                    color = Color(user.color),
                    size = 62,
                    imagePath = user.avatarImage
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.username, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = theme.ink)
                    Text(
                        user.accountName,
                        color = theme.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        user.bio,
                        color = theme.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }

        Button(
            onClick = { showEditor = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
        ) { Text("编辑资料") }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = theme.cardBg
        ) {
            Column(Modifier.padding(8.dp)) {
                ProfileRow(
                    title = "设置",
                    detail = "平板模式、深色模式、服务连接与账号",
                    onClick = onOpenSettings
                )
                androidx.compose.material3.HorizontalDivider(color = theme.divider)
                ProfileRow(
                    title = "关于星屿",
                    detail = "应用特性与版本信息",
                    onClick = onOpenAbout
                )
            }
        }

        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text("恢复演示角色和动态", color = theme.starBlue)
        }
    }

    if (showEditor) {
        UserProfileEditor(
            user = user,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                onUpdateUser(updated)
                showEditor = false
            }
        )
    }
}

@Composable
private fun ProfileRow(title: String, detail: String, onClick: () -> Unit) {
    val theme = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.ink)
            Text(detail, color = theme.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text("\u203A", color = theme.muted, fontSize = 20.sp)
    }
}

@Composable
private fun UserProfileEditor(
    user: UserAccount,
    onDismiss: () -> Unit,
    onSave: (UserAccount) -> Unit
) {
    var username by remember(user.id) { mutableStateOf(user.username) }
    var accountName by remember(user.id) { mutableStateOf(user.accountName) }
    var bio by remember(user.id) { mutableStateOf(user.bio) }
    var avatar by remember(user.id) { mutableStateOf(user.avatar) }
    var color by remember(user.id) { mutableStateOf(user.color) }
    var avatarImage by remember(user.id) { mutableStateOf(user.avatarImage) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text("编辑资料", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "账号名用于下次登录",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(18.dp))

                AvatarEditor(
                    title = "我的头像",
                    text = avatar,
                    color = color,
                    imagePath = avatarImage,
                    onTextChange = { avatar = it },
                    onColorChange = { color = it },
                    onImageChange = { avatarImage = it }
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("账号名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("个性签名") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.height(20.dp))
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (username.isNotBlank() && accountName.isNotBlank()) {
                                onSave(user.copy(
                                    username = username.trim(),
                                    accountName = accountName.trim(),
                                    bio = bio.trim(),
                                    avatar = avatar.ifBlank { username.take(1) },
                                    color = color,
                                    avatarImage = avatarImage
                                ))
                            }
                        },
                        enabled = username.isNotBlank() && accountName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = StarBlue)
                    ) { Text("保存") }
                }
            }
        }
    }
}

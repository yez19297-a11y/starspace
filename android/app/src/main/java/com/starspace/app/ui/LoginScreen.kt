package com.starspace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starspace.app.data.UserAccount

@Composable
fun LoginScreen(
    existingAccount: UserAccount?,
    onCreateAccount: (username: String, accountName: String, password: String, avatar: String, color: Long, avatarImage: String?) -> Unit,
    onSignIn: (accountName: String, password: String) -> Boolean
) {
    var username by remember { mutableStateOf(existingAccount?.username ?: "") }
    var accountName by remember { mutableStateOf(existingAccount?.accountName ?: "") }
    var password by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(existingAccount?.avatar ?: "我") }
    var color by remember { mutableStateOf(existingAccount?.color ?: 0xFF4AAE9B) }
    var avatarImage by remember { mutableStateOf(existingAccount?.avatarImage) }
    var error by remember { mutableStateOf<String?>(null) }
    val isNewAccount = existingAccount == null

    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF397EF6), Color(0xFF7668E8))),
                        CircleShape
                    )
                    .padding(24.dp)
            ) {
                Avatar(
                    text = avatar.ifBlank { "我" },
                    color = Color(color),
                    size = 62,
                    imagePath = avatarImage
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (isNewAccount) "创建星屿身份" else "欢迎回来",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                if (isNewAccount) "用一个名字、账号和头像开始" else "使用账号名和密码继续",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (isNewAccount) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("账号名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )
                    if (isNewAccount) {
                        AvatarEditor(
                            title = "选择头像",
                            text = avatar,
                            color = color,
                            imagePath = avatarImage,
                            onTextChange = { avatar = it },
                            onColorChange = { color = it },
                            onImageChange = { avatarImage = it }
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error ?: "", color = Color(0xFFD85B64), fontSize = 12.sp)
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    if (isNewAccount) {
                        if (username.isBlank() || accountName.isBlank() || password.length < 4) {
                            error = "请填写完整信息，密码至少 4 位"
                        } else {
                            error = null
                            onCreateAccount(username, accountName, password, avatar, color, avatarImage)
                        }
                    } else {
                        if (accountName.isBlank() || password.isBlank()) {
                            error = "请输入账号名和密码"
                        } else if (!onSignIn(accountName, password)) {
                            error = "账号名或密码不正确"
                        } else {
                            error = null
                        }
                    }
                },
                enabled = if (isNewAccount) {
                    username.isNotBlank() && accountName.isNotBlank() && password.length >= 4
                } else {
                    accountName.isNotBlank() && password.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StarBlue)
            ) {
                Text(if (isNewAccount) "进入星屿" else "登录", fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "账号信息仅保存在当前设备，用于本地身份识别",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
}

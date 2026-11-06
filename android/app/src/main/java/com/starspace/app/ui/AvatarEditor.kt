package com.starspace.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID

private val avatarColors = listOf(
    0xFF4AAE9B,
    0xFF6A8EEB,
    0xFF7C68D9,
    0xFFE58A72,
    0xFF52748F,
    0xFFD96E9A
)

@Composable
fun AvatarEditor(
    title: String,
    text: String,
    color: Long,
    imagePath: String?,
    onTextChange: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onImageChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val copied = copyAvatarImage(context, uri)
            if (copied != null) onImageChange(copied)
        }
    }

    Column {
        Text(title, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                text = text.ifBlank { "星" },
                color = Color(color),
                size = 58,
                imagePath = imagePath
            )
            Spacer(Modifier.width(14.dp))
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { onTextChange(it.take(1)) },
                    label = { Text("头像字") },
                    modifier = Modifier.width(92.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    avatarColors.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onColorChange(item) },
                            shape = CircleShape,
                            color = Color(item),
                            border = if (color == item) {
                                androidx.compose.foundation.BorderStroke(3.dp, Ink)
                            } else null
                        ) {}
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StarBlue)
            ) { Text("从相册选择头像") }
            if (imagePath != null) {
                OutlinedButton(onClick = { onImageChange(null) }) {
                    Text("使用文字头像")
                }
            }
        }
    }
}

private fun copyAvatarImage(context: Context, uri: Uri): String? {
    return runCatching {
        val directory = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    }.getOrNull()
}

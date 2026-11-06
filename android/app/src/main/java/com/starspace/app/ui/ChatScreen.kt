package com.starspace.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starspace.app.data.ChatMessage
import com.starspace.app.data.UserAccount

@Composable
fun ChatScreen(
    title: String,
    avatar: String,
    color: Long,
    avatarImage: String?,
    subtitle: String,
    bio: String,
    isGroup: Boolean,
    currentUser: UserAccount,
    messages: List<ChatMessage>,
    sending: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAvatarClick: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val theme = AppTheme.colors

    LaunchedEffect(messages.size, sending) {
        val itemCount = 1 + messages.size + if (sending) 1 else 0
        listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize().background(theme.pageBackground).statusBarsPadding()) {
        Surface(color = theme.cardBg, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = theme.ink)
                }
                Box(Modifier.clickable(onClick = onAvatarClick)) {
                    Avatar(text = avatar, color = Color(color), size = 38, imagePath = avatarImage)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = theme.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(theme.activeDot, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(subtitle, color = theme.muted, fontSize = 11.sp, maxLines = 1)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Settings, if (isGroup) "群聊设置" else "角色设置", tint = theme.muted)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    bio, color = theme.muted, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    conversationAvatar = avatar,
                    conversationColor = color,
                    conversationImage = avatarImage,
                    currentUser = currentUser,
                    isGroup = isGroup
                )
            }
            if (sending) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Avatar(text = avatar, color = Color(color), size = 32, imagePath = avatarImage)
                        Spacer(Modifier.width(8.dp))
                        Surface(color = theme.chatBubbleOther,
                            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在想…", color = theme.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(theme.navBarBg)
                .imePadding().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (isGroup) "在群里说点什么…" else "和 $title 说点什么…",
                        fontSize = 14.sp,
                        color = theme.muted
                    )
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = theme.inputFieldBg,
                    unfocusedContainerColor = theme.inputFieldBg,
                    focusedTextColor = theme.ink,
                    unfocusedTextColor = theme.ink
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !sending) {
                        onSend(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && !sending,
                modifier = Modifier.background(
                    if (input.isNotBlank()) theme.starBlue else theme.sendDisabledBg, CircleShape
                )
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, "发送",
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    conversationAvatar: String,
    conversationColor: Long,
    conversationImage: String?,
    currentUser: UserAccount,
    isGroup: Boolean
) {
    val mine = message.role == "user"
    val authorName = when {
        mine -> currentUser.username
        isGroup -> message.authorName ?: "群成员"
        else -> conversationAvatar
    }
    val authorAvatar = when {
        mine -> currentUser.avatar
        isGroup -> message.authorAvatar ?: conversationAvatar
        else -> conversationAvatar
    }
    val authorColor = when {
        mine -> currentUser.color
        isGroup -> message.authorColor ?: conversationColor
        else -> conversationColor
    }
    val authorImage = when {
        mine -> currentUser.avatarImage
        isGroup -> message.authorImage ?: conversationImage
        else -> conversationImage
    }
    val theme = AppTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Avatar(text = authorAvatar, color = Color(authorColor), size = 32, imagePath = authorImage)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (isGroup && !mine) {
                Text(
                    authorName,
                    color = theme.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            if (!mine && !message.reasoning.isNullOrBlank()) {
                ReasoningBlock(reasoning = message.reasoning!!)
                Spacer(Modifier.height(5.dp))
            }
            Surface(
                modifier = Modifier.widthIn(max = 286.dp),
                color = if (mine) theme.starBlue else theme.chatBubbleOther,
                shape = if (mine) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
                else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
            ) {
                Text(
                    message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    color = if (mine) Color.White else theme.ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    val theme = AppTheme.colors

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = null,
                tint = theme.muted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (expanded) "思考过程" else "思考过程",
                fontSize = 11.sp,
                color = theme.muted,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = theme.muted,
                modifier = Modifier.size(14.dp)
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Surface(
                color = theme.reasoningBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 286.dp).padding(bottom = 2.dp)
            ) {
                Text(
                    reasoning,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = theme.reasoningText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
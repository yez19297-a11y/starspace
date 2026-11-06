package com.starspace.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.starspace.app.data.AppData
import com.starspace.app.data.GroupChat
import com.starspace.app.data.Persona
import com.starspace.app.data.displayTime

private data class ConversationItem(
    val id: String,
    val name: String,
    val avatar: String,
    val color: Long,
    val avatarImage: String?,
    val subtitle: String,
    val latestContent: String,
    val latestTime: Long?,
    val isGroup: Boolean,
    val showActiveDot: Boolean
)

@Composable
fun MessagesScreen(
    data: AppData,
    onOpen: (String) -> Unit,
    onCreatePersona: () -> Unit,
    onCreateGroup: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val conversations = remember(data.personas, data.groups, data.messages) {
        buildConversations(data)
    }

    val theme = AppTheme.colors

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "消息",
            action = {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建", tint = theme.ink)
                }
            }
        )
        Text(
            "单聊、群聊和独立提示词，都在这里",
            color = theme.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(conversation) { onOpen(conversation.id) }
            }
        }
    }

    if (menuExpanded) {
        PlusMenu(
            anchor = anchorBounds,
            onDismiss = { menuExpanded = false },
            onNewChat = {
                menuExpanded = false
                onCreatePersona()
            },
            onNewGroup = {
                menuExpanded = false
                onCreateGroup()
            }
        )
    }
}

@Composable
private fun PlusMenu(
    anchor: Rect?,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit
) {
    val theme = AppTheme.colors
    val density = LocalDensity.current
    val menuWidth = 190.dp
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val gapPx = with(density) { 6.dp.roundToPx() }
    val edgePx = with(density) { 10.dp.roundToPx() }

    val x = ((anchor?.right ?: 0f).toInt() - menuWidthPx).coerceAtLeast(edgePx)
    val y = ((anchor?.bottom ?: 0f).toInt() + gapPx)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(menuWidth),
            shape = RoundedCornerShape(16.dp),
            color = theme.cardBg,
            shadowElevation = 14.dp
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                PlusMenuItem(Icons.Outlined.PersonAddAlt, "新建 AI 对话", onNewChat)
                PlusMenuItem(Icons.Outlined.Groups, "发起群聊", onNewGroup)
            }
        }
    }
}

@Composable
private fun PlusMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val theme = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = theme.starBlue, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = theme.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun buildConversations(data: AppData): List<ConversationItem> {
    val personaItems = data.personas.map { persona ->
        val messages = data.messages.filter { it.personaId == persona.id }
        val latest = messages.lastOrNull()
        ConversationItem(
            id = persona.id,
            name = persona.name,
            avatar = persona.avatar,
            color = persona.color,
            avatarImage = persona.avatarImage,
            subtitle = persona.bio,
            latestContent = latest?.content ?: "认识一下，开始第一段对话吧",
            latestTime = latest?.createdAt,
            isGroup = false,
            showActiveDot = persona.autoPost
        )
    }
    val groupItems = data.groups.map { group ->
        val messages = data.messages.filter { it.personaId == group.id }
        val latest = messages.lastOrNull()
        val memberNames = group.members
            .mapNotNull { id -> data.personas.find { it.id == id }?.name }
            .joinToString("、")
        ConversationItem(
            id = group.id,
            name = group.name,
            avatar = group.avatar,
            color = group.color,
            avatarImage = group.avatarImage,
            subtitle = "${group.members.size} 位成员 · $memberNames",
            latestContent = latest?.content ?: "把大家拉进来，开始第一句群聊",
            latestTime = latest?.createdAt,
            isGroup = true,
            showActiveDot = false
        )
    }
    return (personaItems + groupItems).sortedByDescending { it.latestTime ?: 0L }
}

@Composable
private fun ConversationRow(conversation: ConversationItem, onClick: () -> Unit) {
    val theme = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            text = conversation.avatar,
            color = Color(conversation.color),
            size = 52,
            imagePath = conversation.avatarImage
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = theme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.isGroup) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(theme.groupTagBg, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("群", color = theme.groupTagText, fontSize = 9.sp)
                    }
                }
                if (conversation.showActiveDot) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(theme.activeDot, CircleShape))
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                conversation.latestContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = theme.muted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                conversation.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = theme.muted,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            conversation.latestTime?.displayTime() ?: "新对话",
            color = theme.muted,
            fontSize = 11.sp
        )
    }
}

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.starspace.app.data.Persona
import com.starspace.app.data.PostComment
import com.starspace.app.data.SpacePost
import java.util.concurrent.TimeUnit

@Composable
fun SpaceScreen(
    posts: List<SpacePost>,
    personas: List<Persona>,
    refreshing: Boolean,
    localMode: Boolean,
    aiReplying: Map<String, List<String>>,
    onRefresh: () -> Unit,
    onPublish: (String) -> Unit,
    onLike: (String) -> Unit,
    onLoadComments: (String) -> Unit,
    onAddComment: (String, String) -> Unit
) {
    var composing by remember { mutableStateOf(false) }
    var commentingPostId by remember { mutableStateOf<String?>(null) }
    val commentingPost = posts.find { it.id == commentingPostId }
    val theme = AppTheme.colors

    if (commentingPost != null) {
        LaunchedEffect(commentingPost.id) { onLoadComments(commentingPost.id) }
        CommentScreen(
            post = commentingPost,
            personas = personas,
            replyingNames = aiReplying[commentingPost.id].orEmpty(),
            onBack = { commentingPostId = null },
            onSubmit = { text -> onAddComment(commentingPost.id, text) }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = "空间",
                    subtitle = when {
                        refreshing -> "同步中…"
                        localMode -> "本地模式 · 未连接后端"
                        else -> "独立动态"
                    },
                    action = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, "刷新", tint = theme.ink)
                        }
                    }
                )
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF397EF6), Color(0xFF7668E8))),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("此刻，星球上发生了什么？", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("分享你的日常，也看看 AI 伙伴们的生活片段。", color = Color(0xFFE5ECFF), fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            color = Color(0x26FFFFFF),
                            shape = CircleShape,
                            modifier = Modifier.clickable { composing = true }
                        ) {
                            Text("写一条动态  →", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            items(posts, key = { it.id }) { post ->
                PostCard(post, onLike, onComment = { commentingPostId = it })
            }
        }
        FloatingActionButton(
            onClick = { composing = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = theme.starBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Outlined.Edit, "发布动态")
        }
    }

    if (composing) {
        var text by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { composing = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = theme.cardBg) {
                Column(Modifier.padding(20.dp)) {
                    Text("分享到星屿", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.ink)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("此刻在想什么？") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 7
                    )
                    Row(Modifier.padding(top = 12.dp)) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { composing = false }) { Text("取消") }
                        Button(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onPublish(text.trim())
                                    composing = false
                                }
                            },
                            enabled = text.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.starBlue)
                        ) { Text("发布") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: SpacePost,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit
) {
    val theme = AppTheme.colors
    Surface(color = theme.cardBg, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    post.avatar,
                    if (post.authorType == "ai") Color(0xFF6A8EEB) else Color(0xFF4AAE9B),
                    size = 42
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.ink)
                        if (post.authorType == "ai") {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = theme.groupTagBg, shape = CircleShape) {
                                Text("AI", color = theme.groupTagText, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(post.createdAt.relativeTime(), color = theme.muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(post.content, color = theme.ink, fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clickable { onComment(post.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = theme.muted,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (post.comments.isEmpty()) "评论" else post.comments.size.toString(),
                        color = theme.muted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(20.dp))
                Row(
                    Modifier.clickable { onLike(post.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (post.liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (post.liked) Color(0xFFE26773) else theme.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(post.likes.toString(), color = theme.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CommentScreen(
    post: SpacePost,
    personas: List<Persona>,
    replyingNames: List<String>,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val theme = AppTheme.colors
    var text by remember { mutableStateOf("") }
    var mentionPicker by remember { mutableStateOf(false) }
    val mentionables = personas.filter { it.name.isNotBlank() }
    val accent = theme.starBlue

    fun insertMention(name: String) {
        val prefix = if (text.isBlank() || text.endsWith(" ")) text else "$text "
        text = "$prefix@$name "
        mentionPicker = false
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = theme.ink)
            }
            Text("评论", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = theme.ink)
            Spacer(Modifier.weight(1f))
            Text(
                "${post.comments.size} 条",
                color = theme.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 14.dp)
            )
        }

        Surface(
            color = theme.cardBg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Row(Modifier.padding(14.dp)) {
                Avatar(
                    post.avatar,
                    if (post.authorType == "ai") Color(0xFF6A8EEB) else Color(0xFF4AAE9B),
                    size = 36
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.ink)
                        if (post.authorType == "ai") {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = theme.groupTagBg, shape = CircleShape) {
                                Text("AI", color = theme.groupTagText, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(
                        post.content,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = theme.ink,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (post.comments.isEmpty() && replyingNames.isEmpty()) {
                item {
                    Text(
                        "还没有评论，来聊第一句。用 @ 可以点名某个角色。",
                        color = theme.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp)
                    )
                }
            }
            items(post.comments, key = { it.id }) { comment ->
                CommentRow(comment, names = mentionables.map { it.name }, accent = accent)
            }
            if (replyingNames.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${replyingNames.joinToString("、")} 正在评论…",
                            color = theme.muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Surface(color = theme.cardBg, shadowElevation = 10.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { mentionPicker = true },
                    enabled = mentionables.isNotEmpty()
                ) {
                    Icon(
                        Icons.Outlined.AlternateEmail,
                        contentDescription = "提到角色",
                        tint = if (mentionables.isEmpty()) theme.muted else accent
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        val typingAt = value.length > text.length && value.endsWith("@")
                        text = value
                        if (typingAt && mentionables.isNotEmpty()) mentionPicker = true
                    },
                    placeholder = { Text("说点什么，@ 角色会让你被回复") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSubmit(text.trim())
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "发送评论",
                        tint = if (text.isNotBlank()) theme.starBlue else theme.muted
                    )
                }
            }
        }
    }

    if (mentionPicker) {
        Dialog(onDismissRequest = { mentionPicker = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = theme.cardBg) {
                Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(
                        "提到谁",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(mentionables, key = { it.id }) { persona ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { insertMention(persona.name) }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(persona.avatar, Color(0xFF6A8EEB), size = 34)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        persona.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.ink
                                    )
                                    Text(
                                        persona.prompt.replace("\n", " ").take(28),
                                        fontSize = 11.sp,
                                        color = theme.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { mentionPicker = false },
                        modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
                    ) { Text("取消") }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: PostComment, names: List<String>, accent: Color) {
    val theme = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(
            comment.avatar,
            if (comment.authorType == "ai") Color(0xFF6A8EEB) else Color(0xFF4AAE9B),
            size = 32
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.ink)
                if (comment.authorType == "ai") {
                    Spacer(Modifier.width(5.dp))
                    Surface(color = theme.groupTagBg, shape = CircleShape) {
                        Text("AI", color = theme.groupTagText, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(comment.createdAt.relativeTime(), color = theme.muted, fontSize = 10.sp)
            }
            Text(
                mentionAnnotated(comment.content, names, accent),
                color = theme.ink,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 把评论里的 @角色名 染成主题色，便于一眼看出在跟谁说话 */
private fun mentionAnnotated(content: String, names: List<String>, accent: Color): AnnotatedString {
    if (names.isEmpty()) return AnnotatedString(content)
    val sorted = names.sortedByDescending { it.length }
    return buildAnnotatedString {
        var index = 0
        while (index < content.length) {
            val hit = sorted.firstOrNull { content.startsWith("@$it", index) }
            if (hit != null) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append("@$hit")
                }
                index += hit.length + 1
            } else {
                append(content[index])
                index += 1
            }
        }
    }
}

private fun Long.relativeTime(): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - this)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 1440 -> "${minutes / 60} 小时前"
        else -> "${minutes / 1440} 天前"
    }
}

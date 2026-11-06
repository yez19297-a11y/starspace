package com.starspace.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.starspace.app.data.ApiClient
import com.starspace.app.data.AppLifecycle
import com.starspace.app.data.ChatResult
import com.starspace.app.data.AppStore
import com.starspace.app.data.ChatMessage
import com.starspace.app.data.GroupChat
import com.starspace.app.data.ModelCatalog
import com.starspace.app.data.Persona
import com.starspace.app.data.PostComment
import com.starspace.app.data.SpacePost
import com.starspace.app.notify.Notify
import com.starspace.app.notify.SpaceCheckWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

@Composable
fun StarSpaceApp(
    store: AppStore,
    api: ApiClient,
    initialConversationId: String? = null,
    onInitialConversationConsumed: () -> Unit = {}
) {
    val account = store.data.user
    if (account == null) {
        LoginScreen(
            existingAccount = store.data.savedAccount,
            onCreateAccount = { username, accountName, password, avatar, color, avatarImage ->
                store.createAccount(username, accountName, password, avatar, color, avatarImage)
            },
            onSignIn = { accountName, password ->
                store.signIn(accountName, password)
            }
        )
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(0) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var personaEditorIsNew by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<GroupChat?>(null) }
    var groupEditorIsNew by remember { mutableStateOf(false) }
    var sendingConversationId by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var profilePage by remember { mutableStateOf("profile") }
    var aiReplyingByPost by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // 从通知点进来：直接打开对应会话
    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null) {
            selectedTab = 0
            activeConversationId = initialConversationId
            onInitialConversationConsumed()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) notify("未授予通知权限，AI 回复不会出现在通知栏")
    }

    // 首次进入请求一次通知权限（Android 13+），之后可在系统设置里改
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            store.data.settings.notificationsEnabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            delay(1200)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refreshSpace(manual: Boolean = false) {
        if (refreshing) return
        if (!store.data.settings.backendReady) {
            if (manual) notify("未连接后端，当前为本地模式；可在设置里填写后端地址")
            return
        }
        refreshing = true
        scope.launch {
            try {
                val before = store.data.posts.map { it.id }.toSet()
                val remote = api.fetchPosts(store.data.settings.serverUrl)
                store.replaceRemotePosts(remote)
                if (store.data.settings.notificationsEnabled && !AppLifecycle.foreground) {
                    remote.filter { it.id !in before && it.authorType == "ai" }
                        .take(3)
                        .forEach { post ->
                            Notify.showSpace(
                                context,
                                post.id,
                                "${post.authorName} 发了新动态",
                                post.content
                            )
                        }
                }
            } catch (error: Exception) {
                notify("空间暂未连接：${error.message ?: "请检查后端地址"}")
            } finally {
                refreshing = false
            }
        }
    }

    /**
     * 评论发出后，让 AI 角色读一遍动态和评论再回一句。
     * 被 @ 的角色必回；AI 自己的动态由本人回；你自己的动态随机来一个角色互动。
     */
    fun triggerCommentReplies(postId: String, commentText: String) {
        val post = store.data.posts.find { it.id == postId } ?: return
        val all = store.data.personas
        if (all.isEmpty()) return

        // 长名字优先，避免「小」把「@小满」也匹配走
        var remaining = commentText
        val mentioned = mutableListOf<Persona>()
        all.sortedByDescending { it.name.length }.forEach { persona ->
            val token = "@${persona.name}"
            if (remaining.contains(token)) {
                mentioned.add(persona)
                remaining = remaining.replace(token, "")
            }
        }
        val author = all.find { it.id == post.personaId }
        val responders: List<Pair<Persona, Boolean>> = when {
            mentioned.isNotEmpty() -> mentioned.map { it to true }
            post.authorType == "ai" && author != null -> listOf(author to false)
            else -> listOfNotNull(all.randomOrNull()?.let { it to true })
        }
        if (responders.isEmpty()) return

        val config = store.data.settings.activeConfig
        val backendReady = store.data.settings.backendReady

        scope.launch {
            val pending = responders.map { it.first.name }.toMutableList()
            aiReplyingByPost = aiReplyingByPost + (postId to pending.toList())
            try {
                responders.forEachIndexed { index, (persona, isMentioned) ->
                    if (index > 0) delay(600)
                    delay(500L + Random.nextLong(800L))

                    val latest = store.data.posts.find { it.id == postId } ?: post
                    var produced: PostComment? = null

                    fun local(text: String) = PostComment(
                        id = "ai-local-${UUID.randomUUID()}",
                        postId = postId,
                        authorName = persona.name,
                        avatar = persona.avatar,
                        content = text,
                        authorType = "ai"
                    )

                    if (config.directMode && config.apiKey.isNotBlank()) {
                        val generated = runCatching {
                            api.commentReplyDirect(
                                config, persona, latest.authorName, latest.content,
                                latest.comments, force = true, mentioned = isMentioned
                            )
                        }.getOrNull()
                        if (generated != null) {
                            produced = if (backendReady) {
                                runCatching {
                                    api.postComment(
                                        store.data.settings.serverUrl, postId,
                                        persona.name, persona.avatar, generated, "ai"
                                    )
                                }.getOrElse { local(generated) }
                            } else {
                                local(generated)
                            }
                        }
                    } else if (backendReady) {
                        produced = runCatching {
                            api.aiCommentBackend(
                                store.data.settings.serverUrl, postId, persona.id,
                                force = true, mentioned = isMentioned
                            )
                        }.getOrNull()
                        // 角色没同步到后端时，退回直连
                        if (produced == null && config.apiKey.isNotBlank()) {
                            val generated = runCatching {
                                api.commentReplyDirect(
                                    config, persona, latest.authorName, latest.content,
                                    latest.comments, force = true, mentioned = isMentioned
                                )
                            }.getOrNull()
                            if (generated != null) produced = local(generated)
                        }
                    }

                    val comment = produced
                    if (comment != null) {
                        store.addComment(postId, comment)
                        if (store.data.settings.notificationsEnabled && !AppLifecycle.foreground) {
                            Notify.showSpace(
                                context,
                                "comment-${comment.id}",
                                "${persona.name} 评论了你的动态",
                                comment.content
                            )
                        }
                    }

                    pending.remove(persona.name)
                    aiReplyingByPost = if (pending.isEmpty()) {
                        aiReplyingByPost - postId
                    } else {
                        aiReplyingByPost + (postId to pending.toList())
                    }
                }
            } finally {
                aiReplyingByPost = aiReplyingByPost - postId
            }
        }
    }

    LaunchedEffect(Unit) {
        if (store.data.settings.backendReady) {
            val serverUrl = store.data.settings.serverUrl
            store.data.personas.forEach { persona ->
                runCatching { api.syncPersona(serverUrl, persona) }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) refreshSpace()
    }

    BackHandler(
        enabled = activeConversationId != null || profilePage != "profile"
    ) {
        when {
            activeConversationId != null -> activeConversationId = null
            profilePage == "about" || profilePage == "llm" || profilePage == "backup" ->
                profilePage = "settings"
            else -> profilePage = "profile"
        }
    }

    BoxWithConstraints {
        val tabletLayout = store.data.settings.tabletMode && maxWidth >= 600.dp
        val theme = AppTheme.colors

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = theme.pageBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (activeConversationId == null || tabletLayout) {
                    NavigationBar(containerColor = theme.navBarBg) {
                    val items = listOf(
                        Triple("消息", Icons.Outlined.ChatBubbleOutline, "消息"),
                        Triple("空间", Icons.Outlined.Public, "空间"),
                        Triple("我的", Icons.Outlined.PersonOutline, "我的")
                    )
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                if (index != 0) activeConversationId = null
                                if (index == 2) profilePage = "profile"
                            },
                            icon = { Icon(item.second, item.third) },
                            label = { Text(item.first) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = theme.starBlue,
                                selectedTextColor = theme.starBlue,
                                indicatorColor = theme.navItemSelectedBg,
                                unselectedIconColor = theme.muted,
                                unselectedTextColor = theme.muted
                            )
                        )
                    }
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(theme.pageBackground)
            ) {
                val activePersona = store.data.personas.find { it.id == activeConversationId }
                val activeGroup = store.data.groups.find { it.id == activeConversationId }

                when {
                    selectedTab == 0 && tabletLayout -> {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.34f)) {
                                MessagesScreen(
                                    data = store.data,
                                    onOpen = { activeConversationId = it },
                                    onCreatePersona = {
                                        editingPersona = Persona(
                                            name = "",
                                            avatar = "星",
                                            model = "",
                                            prompt = "你是一位真诚、有个性的朋友。用自然的方式和用户交流，并偶尔分享自己的生活片段。"
                                        )
                                        personaEditorIsNew = true
                                    },
                                    onCreateGroup = {
                                        editingGroup = GroupChat(
                                            name = "",
                                            avatar = "群",
                                            members = emptyList()
                                        )
                                        groupEditorIsNew = true
                                    }
                                )
                            }
                            Box(Modifier.weight(0.66f)) {
                                when {
                                    activePersona != null -> SingleChatPane(
                                        store = store,
                                        api = api,
                                        persona = activePersona,
                                        currentUser = account,
                                        sending = sendingConversationId == activePersona.id,
                                        onEdit = {
                                            editingPersona = activePersona
                                            personaEditorIsNew = false
                                        },
                                        onNotify = { notify(it) },
                                        onSendingChange = { sendingConversationId = it }
                                    )
                                    activeGroup != null -> GroupChatPane(
                                        store = store,
                                        api = api,
                                        group = activeGroup,
                                        currentUser = account,
                                        sending = sendingConversationId == activeGroup.id,
                                        onEdit = {
                                            editingGroup = activeGroup
                                            groupEditorIsNew = false
                                        },
                                        onNotify = { notify(it) },
                                        onSendingChange = { sendingConversationId = it }
                                    )
                                    else -> TabletChatPlaceholder()
                                }
                            }
                        }
                    }
                    selectedTab == 0 && activePersona != null -> {
                        SingleChatPane(
                            store = store,
                            api = api,
                            persona = activePersona,
                            currentUser = account,
                            sending = sendingConversationId == activePersona.id,
                            onBack = { activeConversationId = null },
                            onEdit = {
                                editingPersona = activePersona
                                personaEditorIsNew = false
                            },
                            onNotify = { notify(it) },
                            onSendingChange = { sendingConversationId = it }
                        )
                    }
                    selectedTab == 0 && activeGroup != null -> {
                        GroupChatPane(
                            store = store,
                            api = api,
                            group = activeGroup,
                            currentUser = account,
                            sending = sendingConversationId == activeGroup.id,
                            onBack = { activeConversationId = null },
                            onEdit = {
                                editingGroup = activeGroup
                                groupEditorIsNew = false
                            },
                            onNotify = { notify(it) },
                            onSendingChange = { sendingConversationId = it }
                        )
                    }
                    selectedTab == 0 -> {
                        MessagesScreen(
                            data = store.data,
                            onOpen = { activeConversationId = it },
                            onCreatePersona = {
                                editingPersona = Persona(
                                    name = "",
                                    avatar = "星",
                                    model = "",
                                    prompt = "你是一位真诚、有个性的朋友。用自然的方式和用户交流，并偶尔分享自己的生活片段。"
                                )
                                personaEditorIsNew = true
                            },
                            onCreateGroup = {
                                editingGroup = GroupChat(
                                    name = "",
                                    avatar = "群",
                                    members = emptyList()
                                )
                                groupEditorIsNew = true
                            }
                        )
                    }
                    selectedTab == 1 -> {
                        SpaceScreen(
                            posts = store.data.posts,
                            personas = store.data.personas,
                            refreshing = refreshing,
                            localMode = !store.data.settings.backendReady,
                            aiReplying = aiReplyingByPost,
                            onRefresh = { refreshSpace(manual = true) },
                            onPublish = { content ->
                                if (!store.data.settings.backendReady) {
                                    store.addPost(
                                        SpacePost(
                                            id = "local-${UUID.randomUUID()}",
                                            authorName = account.username,
                                            avatar = account.avatar,
                                            content = content
                                        )
                                    )
                                    notify("已保存到本机")
                                } else {
                                    scope.launch {
                                        try {
                                            store.addPost(
                                                api.publishPost(
                                                    store.data.settings.serverUrl,
                                                    account,
                                                    content
                                                )
                                            )
                                        } catch (error: Exception) {
                                            store.addPost(
                                                SpacePost(
                                                    id = "local-${UUID.randomUUID()}",
                                                    authorName = account.username,
                                                    avatar = account.avatar,
                                                    content = content
                                                )
                                            )
                                            notify("已保存在本机：${error.message ?: "后端未连接"}")
                                        }
                                    }
                                }
                            },
                            onLike = store::toggleLike,
                            onLoadComments = { postId ->
                                if (store.data.settings.backendReady) {
                                    scope.launch {
                                        runCatching {
                                            api.fetchComments(store.data.settings.serverUrl, postId)
                                        }.onSuccess { comments ->
                                            store.replaceComments(postId, comments)
                                        }
                                    }
                                }
                            },
                            onAddComment = { postId, content ->
                                val local = PostComment(
                                    id = "local-${UUID.randomUUID()}",
                                    postId = postId,
                                    authorName = account.username,
                                    avatar = account.avatar,
                                    content = content
                                )
                                store.addComment(postId, local)
                                scope.launch {
                                    if (store.data.settings.backendReady) {
                                        try {
                                            val saved = api.addComment(
                                                store.data.settings.serverUrl, postId, account, content
                                            )
                                            store.replaceComment(postId, local.id, saved)
                                        } catch (error: Exception) {
                                            notify("评论已保存在本机：${error.message ?: "后端未连接"}")
                                        }
                                    }
                                    triggerCommentReplies(postId, content)
                                }
                            }
                        )
                    }
                    else -> {
                        when (profilePage) {
                            "settings" -> SettingsScreen(
                                user = account,
                                settings = store.data.settings,
                                onBack = { profilePage = "profile" },
                                onToggleTabletMode = { enabled ->
                                    store.setTabletMode(enabled)
                                    notify(if (enabled) "平板模式已开启" else "平板模式已关闭")
                                },
                                onSetDarkMode = { mode ->
                                    store.setDarkMode(mode)
                                    notify("深色模式已切换")
                                },
                                onToggleNotifications = { enabled ->
                                    store.setNotificationsEnabled(enabled)
                                    if (enabled) {
                                        SpaceCheckWorker.schedule(context)
                                    } else {
                                        SpaceCheckWorker.cancel(context)
                                    }
                                    notify(if (enabled) "通知已开启" else "通知已关闭")
                                },
                                onSaveServerUrl = { url ->
                                    store.updateServerUrl(url)
                                    if (!store.data.settings.backendReady) {
                                        notify("已切换为本地模式，不会再尝试连接后端")
                                    } else {
                                        scope.launch {
                                            var synced = 0
                                            store.data.personas.forEach { persona ->
                                                runCatching {
                                                    api.syncPersona(store.data.settings.serverUrl, persona)
                                                }.onSuccess { synced += 1 }
                                            }
                                            notify("后端地址已保存，已同步 $synced 个角色")
                                        }
                                    }
                                },
                                onOpenLLMConfig = { profilePage = "llm" },
                                onOpenBackup = { profilePage = "backup" },
                                onOpenAbout = { profilePage = "about" },
                                onLogout = {
                                    store.logout()
                                }
                            )
                            "llm" -> LLMConfigScreen(
                                configs = store.data.settings.llmConfigs,
                                activeConfigId = store.data.settings.activeConfigId,
                                onBack = { profilePage = "settings" },
                                onSave = { newConfig ->
                                    val toSave = newConfig.copy(connected = false)
                                    store.updateLLMConfig(toSave)
                                    scope.launch {
                                        try {
                                            if (newConfig.directMode) {
                                                api.testDirectConnection(newConfig)
                                                store.updateLLMConfig(newConfig.copy(connected = true))
                                                notify("「${newConfig.name}」已保存并设为当前配置")
                                            } else if (!store.data.settings.backendReady) {
                                                notify("已保存配置，但后端地址为空，暂时无法中转")
                                            } else {
                                                api.updateBackendLLM(
                                                    store.data.settings.serverUrl, newConfig
                                                )
                                                store.updateLLMConfig(newConfig.copy(connected = true))
                                                notify("「${newConfig.name}」已保存并同步到后端")
                                            }
                                        } catch (error: Exception) {
                                            notify("配置已保存，但连接测试失败：${error.message}")
                                        }
                                    }
                                },
                                onDelete = { id ->
                                    store.deleteLLMConfig(id)
                                    notify("配置已删除")
                                },
                                onActivate = { config ->
                                    store.activateLLMConfig(config.id)
                                    if (config.directMode) {
                                        notify("已切换到「${config.name}」")
                                    } else if (!store.data.settings.backendReady) {
                                        notify("已切换到「${config.name}」，但后端地址尚未配置")
                                    } else {
                                        scope.launch {
                                            try {
                                                api.updateBackendLLM(
                                                    store.data.settings.serverUrl, config
                                                )
                                                notify("已切换到「${config.name}」并同步到后端")
                                            } catch (error: Exception) {
                                                notify("已切换，但后端同步失败：${error.message}")
                                            }
                                        }
                                    }
                                },
                                onTestDirect = { config, callback ->
                                    scope.launch {
                                        try {
                                            val reply = api.testDirectConnection(config)
                                            callback(Result.success(reply))
                                        } catch (error: Exception) {
                                            callback(Result.failure(error))
                                        }
                                    }
                                },
                                onTestBackend = { callback ->
                                    if (!store.data.settings.backendReady) {
                                        callback(Result.failure(IllegalStateException("尚未配置后端地址")))
                                    } else {
                                        scope.launch {
                                            try {
                                                val reply = api.testBackendConnection(
                                                    store.data.settings.serverUrl
                                                )
                                                callback(Result.success(reply))
                                            } catch (error: Exception) {
                                                callback(Result.failure(error))
                                            }
                                        }
                                    }
                                }
                            )
                            "backup" -> BackupScreen(
                                onBack = { profilePage = "settings" },
                                buildBackup = { includeContent -> store.exportBackup(includeContent) },
                                onRestore = { raw -> store.importBackup(raw) },
                                onNotify = { message -> notify(message) }
                            )
                            "about" -> AboutScreen(
                                onBack = { profilePage = "profile" }
                            )
                            else -> ProfileScreen(
                                user = account,
                                onOpenSettings = { profilePage = "settings" },
                                onOpenAbout = { profilePage = "about" },
                                onUpdateUser = { updated ->
                                    store.updateUser(updated)
                                    notify("资料已更新")
                                },
                                onReset = {
                                    store.resetDemo()
                                    notify("演示数据已恢复")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    editingPersona?.let { persona ->
        PersonaEditor(
            persona = persona,
            isNew = personaEditorIsNew,
            modelOptions = (
                ModelCatalog.all +
                    store.data.settings.llmConfigs.map { it.defaultModel } +
                    persona.model
                ).filter { it.isNotBlank() }.distinct(),
            onDismiss = { editingPersona = null },
            onSave = { updated ->
                if (personaEditorIsNew) store.addPersona(updated) else store.updatePersona(updated)
                editingPersona = null
                if (store.data.settings.backendReady) {
                    scope.launch {
                        try {
                            api.syncPersona(store.data.settings.serverUrl, updated)
                            if (updated.autoPost) notify("自动动态计划已同步")
                        } catch (error: Exception) {
                            notify("角色已保存在本机；自动动态需连接后端后生效")
                        }
                    }
                } else if (updated.autoPost) {
                    notify("角色已保存；自动发动态需要先连接后端")
                }
            },
            onDelete = {
                store.deletePersona(persona.id)
                editingPersona = null
                activeConversationId = null
                if (store.data.settings.backendReady) {
                    scope.launch {
                        runCatching {
                            api.deleteRemotePersona(store.data.settings.serverUrl, persona.id)
                        }
                    }
                }
            }
        )
    }

    editingGroup?.let { group ->
        GroupEditor(
            group = group,
            personas = store.data.personas,
            isNew = groupEditorIsNew,
            onDismiss = { editingGroup = null },
            onSave = { updated ->
                if (groupEditorIsNew) store.addGroup(updated) else store.updateGroup(updated)
                editingGroup = null
                activeConversationId = updated.id
                notify("群聊已保存")
            },
            onDelete = {
                store.deleteGroup(group.id)
                editingGroup = null
                activeConversationId = null
                notify("群聊已解散")
            }
        )
    }
}

@Composable
private fun SingleChatPane(
    store: AppStore,
    api: ApiClient,
    persona: Persona,
    currentUser: com.starspace.app.data.UserAccount,
    sending: Boolean,
    onBack: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onNotify: (String) -> Unit,
    onSendingChange: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messages = store.data.messages.filter { it.personaId == persona.id }

    ChatScreen(
        title = persona.name,
        avatar = persona.avatar,
        color = persona.color,
        avatarImage = persona.avatarImage,
        subtitle = "在星屿等你",
        bio = persona.bio,
        isGroup = false,
        currentUser = currentUser,
        messages = messages,
        sending = sending,
        onBack = { onBack?.invoke() },
        onEdit = onEdit,
        onAvatarClick = onEdit,
        onSend = { text ->
            val userMessage = ChatMessage(
                personaId = persona.id,
                role = "user",
                content = text,
                authorId = currentUser.id,
                authorName = currentUser.username,
                authorAvatar = currentUser.avatar,
                authorColor = currentUser.color,
                authorImage = currentUser.avatarImage
            )
            store.addMessage(userMessage)
            onSendingChange(persona.id)
            scope.launch {
                try {
                    val history = store.data.messages.filter { it.personaId == persona.id }
                    val llm = store.data.settings.activeConfig
                    val result = if (llm.directMode && llm.apiKey.isNotBlank()) {
                        api.chatDirect(llm, persona, history)
                    } else if (store.data.settings.backendReady) {
                        api.chat(store.data.settings.serverUrl, persona, history)
                    } else {
                        onNotify("请先在设置里填写后端地址，或切换到直连配置")
                        onSendingChange(null)
                        return@launch
                    }
                    store.addMessage(
                        ChatMessage(
                            personaId = persona.id,
                            role = "assistant",
                            content = result.content,
                            reasoning = result.reasoning,
                            authorId = persona.id,
                            authorName = persona.name,
                            authorAvatar = persona.avatar,
                            authorColor = persona.color,
                            authorImage = persona.avatarImage
                        )
                    )
                    if (store.data.settings.notificationsEnabled && !AppLifecycle.foreground) {
                        Notify.showMessage(
                            context, persona.id, persona.name, result.content
                        )
                    }
                } catch (error: Exception) {
                    onNotify(error.message ?: "AI 服务连接失败")
                } finally {
                    onSendingChange(null)
                }
            }
        }
    )
}

@Composable
private fun GroupChatPane(
    store: AppStore,
    api: ApiClient,
    group: GroupChat,
    currentUser: com.starspace.app.data.UserAccount,
    sending: Boolean,
    onBack: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onNotify: (String) -> Unit,
    onSendingChange: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messages = store.data.messages.filter { it.personaId == group.id }
    val members = group.members.mapNotNull { id ->
        store.data.personas.find { it.id == id }
    }
    val memberNames = members.joinToString("、") { it.name }

    ChatScreen(
        title = group.name,
        avatar = group.avatar,
        color = group.color,
        avatarImage = group.avatarImage,
        subtitle = "${members.size} 位成员 · $memberNames",
        bio = "群里的每位 AI 伙伴都会独立思考，决定是否参与。@某人可指定回复",
        isGroup = true,
        currentUser = currentUser,
        messages = messages,
        sending = sending,
        onBack = { onBack?.invoke() },
        onEdit = onEdit,
        onAvatarClick = onEdit,
        onSend = { text ->
            if (members.isEmpty()) {
                onNotify("请先在群聊设置里选择成员")
                return@ChatScreen
            }
            val userMessage = ChatMessage(
                personaId = group.id,
                role = "user",
                content = text,
                authorId = currentUser.id,
                authorName = currentUser.username,
                authorAvatar = currentUser.avatar,
                authorColor = currentUser.color,
                authorImage = currentUser.avatarImage
            )
            store.addMessage(userMessage)
            onSendingChange(group.id)

            val mentionedMembers = members.filter { member ->
                text.contains("@${member.name}") || text.contains("@${member.avatar}")
            }
            val otherMembers = members.filter { it !in mentionedMembers }

            scope.launch {
                try {
                    val history = store.data.messages
                        .filter { it.personaId == group.id }

                    val responders = mutableListOf<Pair<Persona, ChatResult>>()
                    val llm = store.data.settings.activeConfig
                    val direct = llm.directMode && llm.apiKey.isNotBlank()

                    if (!direct && !store.data.settings.backendReady) {
                        onNotify("请先在设置里填写后端地址，或切换到直连配置")
                        onSendingChange(null)
                        return@launch
                    }

                    for (member in mentionedMembers) {
                        val result = if (direct) {
                            api.groupDecideDirect(
                                llm, member, history,
                                members.map { it.name }, mentioned = true
                            )
                        } else {
                            api.chat(store.data.settings.serverUrl, member, history)
                        }
                        if (result.shouldReply && result.content.isNotBlank()) {
                            responders.add(member to result)
                        }
                    }

                    for (member in otherMembers) {
                        val result = if (direct) {
                            api.groupDecideDirect(
                                llm, member, history,
                                members.map { it.name }, mentioned = false
                            )
                        } else {
                            api.chat(store.data.settings.serverUrl, member, history)
                        }
                        if (result.shouldReply && result.content.isNotBlank()) {
                            responders.add(member to result)
                        }
                    }

                    for ((member, result) in responders) {
                        store.addMessage(
                            ChatMessage(
                                personaId = group.id,
                                role = "assistant",
                                content = result.content,
                                reasoning = result.reasoning,
                                authorId = member.id,
                                authorName = member.name,
                                authorAvatar = member.avatar,
                                authorColor = member.color,
                                authorImage = member.avatarImage
                            )
                        )
                    }

                    if (responders.isEmpty()) {
                        onNotify("大家这次都选择了沉默")
                    } else if (store.data.settings.notificationsEnabled && !AppLifecycle.foreground) {
                        val (member, result) = responders.last()
                        Notify.showMessage(
                            context,
                            group.id,
                            "${group.name} · ${member.name}",
                            result.content
                        )
                    }
                } catch (error: Exception) {
                    onNotify(error.message ?: "AI 服务连接失败")
                } finally {
                    onSendingChange(null)
                }
            }
        }
    )
}

@Composable
private fun TabletChatPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("选择一个对话", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "平板模式会在左侧保留消息列表，右侧显示当前对话。",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

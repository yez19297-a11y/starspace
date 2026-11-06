package com.starspace.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class AppStore(context: Context) {
    private val preferences = context.getSharedPreferences("star_space_data", Context.MODE_PRIVATE)

    var data by mutableStateOf(load())
        private set

    fun createAccount(
        username: String,
        accountName: String,
        password: String,
        avatar: String,
        color: Long,
        avatarImage: String?
    ) {
        val account = UserAccount(
            username = username.trim(),
            accountName = accountName.trim(),
            passwordHash = hashPassword(password),
            avatar = avatar.ifBlank { username.take(1) },
            color = color,
            avatarImage = avatarImage
        )
        update(data.copy(user = account, savedAccount = account))
    }

    fun signIn(accountName: String, password: String): Boolean {
        val account = data.savedAccount ?: return false
        if (account.accountName != accountName.trim()) return false
        if (account.passwordHash != hashPassword(password)) return false
        update(data.copy(user = account))
        return true
    }

    fun logout() {
        update(data.copy(user = null))
    }

    fun updateUser(account: UserAccount) {
        update(data.copy(user = account, savedAccount = account))
    }

    fun addPersona(persona: Persona) {
        update(data.copy(personas = listOf(persona) + data.personas))
    }

    fun updatePersona(persona: Persona) {
        val posts = data.posts.map {
            if (it.personaId == persona.id) it.copy(authorName = persona.name, avatar = persona.avatar) else it
        }
        update(data.copy(
            personas = data.personas.map { if (it.id == persona.id) persona else it },
            posts = posts
        ))
    }

    fun deletePersona(personaId: String) {
        update(data.copy(
            personas = data.personas.filterNot { it.id == personaId },
            groups = data.groups.map { group ->
                group.copy(members = group.members.filterNot { it == personaId })
            },
            messages = data.messages.filterNot { it.personaId == personaId },
            posts = data.posts.filterNot { it.personaId == personaId }
        ))
    }

    fun addGroup(group: GroupChat) {
        update(data.copy(groups = listOf(group) + data.groups))
    }

    fun updateGroup(group: GroupChat) {
        update(data.copy(groups = data.groups.map { if (it.id == group.id) group else it }))
    }

    fun deleteGroup(groupId: String) {
        update(data.copy(
            groups = data.groups.filterNot { it.id == groupId },
            messages = data.messages.filterNot { it.personaId == groupId }
        ))
    }

    fun addMessage(message: ChatMessage) {
        update(data.copy(messages = data.messages + message))
    }

    fun addPost(post: SpacePost) {
        update(data.copy(posts = listOf(post) + data.posts))
    }

    fun replaceRemotePosts(posts: List<SpacePost>) {
        val localById = data.posts.associateBy { it.id }
        val localOnly = data.posts.filter { it.id.startsWith("local-") }
        val merged = posts.map { remote ->
            val pendingLocal = localById[remote.id]?.comments
                ?.filter { it.isPendingLocal() }
                .orEmpty()
            if (pendingLocal.isEmpty()) remote
            else remote.copy(comments = (remote.comments + pendingLocal).distinctBy { it.id })
        }
        update(data.copy(posts = (merged + localOnly).distinctBy { it.id }.sortedByDescending { it.createdAt }))
    }

    fun addComment(postId: String, comment: PostComment) {
        update(data.copy(posts = data.posts.map { post ->
            if (post.id == postId) post.copy(comments = post.comments + comment) else post
        }))
    }

    fun replaceComment(postId: String, localId: String, saved: PostComment) {
        update(data.copy(posts = data.posts.map { post ->
            if (post.id == postId) {
                post.copy(comments = post.comments.map { if (it.id == localId) saved else it })
            } else {
                post
            }
        }))
    }

    fun replaceComments(postId: String, comments: List<PostComment>) {
        update(data.copy(posts = data.posts.map { post ->
            if (post.id == postId) {
                val pendingLocal = post.comments.filter { it.isPendingLocal() }
                post.copy(comments = (comments + pendingLocal).distinctBy { it.id })
            } else {
                post
            }
        }))
    }

    fun toggleLike(postId: String) {
        update(data.copy(posts = data.posts.map {
            if (it.id == postId) it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) else it
        }))
    }

    fun updateServerUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        update(data.copy(settings = data.settings.copy(
            serverUrl = cleaned,
            serverConfigured = cleaned.isNotBlank()
        )))
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        update(data.copy(settings = data.settings.copy(notificationsEnabled = enabled)))
    }

    /** 导出备份。includeContent = false 时只带角色、群组和设置，不含聊天与动态。 */
    fun exportBackup(includeContent: Boolean): String {
        val payload = data.toJson()
        if (!includeContent) {
            payload.put("messages", JSONArray())
            payload.put("posts", JSONArray())
        }
        return JSONObject()
            .put("app", "StarSpace")
            .put("format", 1)
            .put("kind", if (includeContent) "full" else "config")
            .put("exportedAt", System.currentTimeMillis())
            .put("data", payload)
            .toString(2)
    }

    /** 恢复备份，返回一句结果描述。完整备份会保留当前登录状态，避免恢复后被登出。 */
    fun importBackup(raw: String): String {
        val root = JSONObject(raw)
        require(root.optString("app") == "StarSpace") { "这不是星屿的备份文件" }
        val kind = root.optString("kind", "full")
        val parsed = root.getJSONObject("data").toString().toAppData()
        val current = data
        val restored = if (kind == "config") {
            parsed.copy(
                user = current.user,
                savedAccount = current.savedAccount,
                messages = current.messages,
                posts = current.posts
            )
        } else {
            parsed.copy(
                user = current.user,
                savedAccount = current.savedAccount ?: parsed.savedAccount
            )
        }
        update(restored)
        return if (kind == "config") {
            "配置已恢复：${restored.personas.size} 个角色、${restored.groups.size} 个群组"
        } else {
            "完整备份已恢复：${restored.personas.size} 个角色、${restored.messages.size} 条消息"
        }
    }

    fun setTabletMode(enabled: Boolean) {
        update(data.copy(settings = data.settings.copy(tabletMode = enabled)))
    }

    fun setDarkMode(mode: String) {
        update(data.copy(settings = data.settings.copy(darkMode = mode)))
    }

    fun updateLLMConfig(config: LLMConfig) {
        val exists = data.settings.llmConfigs.any { it.id == config.id }
        val list = if (exists) {
            data.settings.llmConfigs.map { if (it.id == config.id) config else it }
        } else {
            data.settings.llmConfigs + config
        }
        update(
            data.copy(
                settings = data.settings.copy(
                    llmConfigs = list,
                    activeConfigId = config.id
                )
            )
        )
    }

    fun deleteLLMConfig(id: String) {
        val remaining = data.settings.llmConfigs.filterNot { it.id == id }
        val activeId = if (data.settings.activeConfigId == id) {
            remaining.firstOrNull()?.id ?: ""
        } else {
            data.settings.activeConfigId
        }
        update(
            data.copy(
                settings = data.settings.copy(
                    llmConfigs = remaining,
                    activeConfigId = activeId
                )
            )
        )
    }

    fun activateLLMConfig(id: String) {
        if (data.settings.llmConfigs.none { it.id == id }) return
        update(data.copy(settings = data.settings.copy(activeConfigId = id)))
    }

    fun resetDemo() {
        val currentUser = data.user
        val savedAccount = data.savedAccount
        val currentSettings = data.settings
        update(sampleData().copy(user = currentUser, savedAccount = savedAccount, settings = currentSettings))
    }

    private fun update(newData: AppData) {
        data = newData
        preferences.edit().putString("app_data", newData.toJson().toString()).apply()
    }

    private fun load(): AppData {
        val raw = preferences.getString("app_data", null) ?: return sampleData()
        return try { raw.toAppData() } catch (_: Exception) { sampleData() }
    }
}

private fun AppData.toJson() = JSONObject().apply {
    put("user", user?.toJson() ?: JSONObject.NULL)
    put("savedAccount", savedAccount?.toJson() ?: JSONObject.NULL)
    put("settings", JSONObject()
        .put("serverUrl", settings.serverUrl)
        .put("serverConfigured", settings.serverConfigured)
        .put("tabletMode", settings.tabletMode)
        .put("darkMode", settings.darkMode)
        .put("notificationsEnabled", settings.notificationsEnabled)
        .put("activeConfigId", settings.activeConfigId)
        .put("llmConfigs", JSONArray().apply {
            settings.llmConfigs.forEach { put(it.toJson()) }
        }))
    put("personas", JSONArray().apply { personas.forEach { put(it.toJson()) } })
    put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
    put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    put("posts", JSONArray().apply { posts.forEach { put(it.toJson()) } })
}

private fun LLMConfig.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("directMode", directMode)
    put("apiKey", apiKey)
    put("baseUrl", baseUrl)
    put("defaultModel", defaultModel)
    put("connected", connected)
}

private fun UserAccount.toJson() = JSONObject().apply {
    put("id", id)
    put("username", username)
    put("accountName", accountName)
    put("passwordHash", passwordHash)
    put("avatar", avatar)
    put("color", color)
    put("avatarImage", avatarImage ?: JSONObject.NULL)
    put("bio", bio)
}

private fun Persona.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("avatar", avatar)
    put("color", color)
    put("avatarImage", avatarImage ?: JSONObject.NULL)
    put("prompt", prompt)
    put("model", model)
    put("temperature", temperature)
    put("autoPost", autoPost)
    put("minPostHours", minPostHours)
    put("maxPostHours", maxPostHours)
    put("bio", bio)
}

private fun GroupChat.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("avatar", avatar)
    put("color", color)
    put("avatarImage", avatarImage ?: JSONObject.NULL)
    put("members", JSONArray().apply { members.forEach { put(it) } })
}

private fun ChatMessage.toJson() = JSONObject().apply {
    put("id", id)
    put("personaId", personaId)
    put("role", role)
    put("content", content)
    put("createdAt", createdAt)
    put("authorId", authorId ?: JSONObject.NULL)
    put("authorName", authorName ?: JSONObject.NULL)
    put("authorAvatar", authorAvatar ?: JSONObject.NULL)
    put("authorColor", authorColor ?: JSONObject.NULL)
    put("authorImage", authorImage ?: JSONObject.NULL)
    put("reasoning", reasoning ?: JSONObject.NULL)
}

private fun SpacePost.toJson() = JSONObject().apply {
    put("id", id)
    put("personaId", personaId ?: JSONObject.NULL)
    put("authorName", authorName)
    put("avatar", avatar)
    put("content", content)
    put("createdAt", createdAt)
    put("likes", likes)
    put("liked", liked)
    put("authorType", authorType)
    put("comments", JSONArray().apply { comments.forEach { put(it.toJson()) } })
}

private fun PostComment.toJson() = JSONObject().apply {
    put("id", id)
    put("postId", postId)
    put("authorName", authorName)
    put("avatar", avatar)
    put("content", content)
    put("createdAt", createdAt)
    put("authorType", authorType)
}

private fun String.toAppData(): AppData {
    val root = JSONObject(this)
    val userJson = root.optJSONObject("user")
    val savedAccountJson = root.optJSONObject("savedAccount")
    val settingsJson = root.optJSONObject("settings")
    val user = userJson?.toUser()
    val savedAccount = savedAccountJson?.toUser() ?: user
    val personas = root.getJSONArray("personas").mapObjects { it.toPersona() }
    val groups = root.optJSONArray("groups")?.mapObjects { it.toGroup() } ?: emptyList()
    val messages = root.getJSONArray("messages").mapObjects { it.toMessage() }
    val posts = root.getJSONArray("posts").mapObjects { it.toPost() }
    val llmArray = settingsJson?.optJSONArray("llmConfigs")
    val legacyLlm = settingsJson?.optJSONObject("llmConfig")
    val llmConfigs = when {
        llmArray != null -> llmArray.mapObjects { it.toLLMConfig() }
        legacyLlm != null -> listOf(legacyLlm.toLLMConfig())
        else -> emptyList()
    }
    val savedActiveId = settingsJson?.optString("activeConfigId", "").orEmpty()
    val activeId = if (llmConfigs.any { it.id == savedActiveId }) savedActiveId
        else llmConfigs.firstOrNull()?.id ?: ""
    return AppData(
        user = user,
        savedAccount = savedAccount,
        personas = personas,
        groups = groups,
        messages = messages,
        posts = posts,
        settings = AppSettings(
            serverUrl = settingsJson?.optString("serverUrl").orEmpty(),
            // 老版本没有这个标记：即使存着默认的 10.0.2.2 也视为未配置，避免真机反复超时
            serverConfigured = settingsJson?.optBoolean("serverConfigured", false) ?: false,
            tabletMode = settingsJson?.optBoolean("tabletMode", false) ?: false,
            darkMode = settingsJson?.optString("darkMode", "system") ?: "system",
            notificationsEnabled = settingsJson?.optBoolean("notificationsEnabled", true) ?: true,
            llmConfigs = llmConfigs,
            activeConfigId = activeId
        )
    )
}

private fun JSONObject.toLLMConfig() = LLMConfig(
    id = optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
    name = optString("name", "").ifBlank { "我的配置" },
    directMode = optBoolean("directMode", true),
    apiKey = optString("apiKey", ""),
    baseUrl = optString("baseUrl", "https://api.openai.com/v1")
        .ifBlank { "https://api.openai.com/v1" },
    defaultModel = optString("defaultModel", "gpt-4o-mini")
        .ifBlank { "gpt-4o-mini" },
    connected = optBoolean("connected", false)
)

private fun JSONObject.toUser() = UserAccount(
    id = getString("id"),
    username = getString("username"),
    accountName = getString("accountName"),
    passwordHash = getString("passwordHash"),
    avatar = optString("avatar", "我"),
    color = optLong("color", 0xFF4AAE9B),
    avatarImage = optionalString("avatarImage"),
    bio = optString("bio", "把日常和灵感，安放在自己的小宇宙")
)

private fun JSONObject.toPersona() = Persona(
    id = getString("id"),
    name = getString("name"),
    avatar = optString("avatar", "星"),
    color = optLong("color", 0xFF5B8DEF),
    avatarImage = optionalString("avatarImage"),
    prompt = getString("prompt"),
    model = optString("model", "gpt-4o-mini"),
    temperature = optDouble("temperature", 0.8),
    autoPost = optBoolean("autoPost", false),
    minPostHours = optInt("minPostHours", 4),
    maxPostHours = optInt("maxPostHours", 12),
    bio = optString("bio", "一个住在星屿里的 AI 朋友")
)

private fun JSONObject.toGroup() = GroupChat(
    id = getString("id"),
    name = getString("name"),
    avatar = optString("avatar", "群"),
    color = optLong("color", 0xFF52748F),
    avatarImage = optionalString("avatarImage"),
    members = optJSONArray("members")?.let { array ->
        (0 until array.length()).map { array.optString(it) }
    } ?: emptyList()
)

private fun JSONObject.toMessage() = ChatMessage(
    id = getString("id"),
    personaId = getString("personaId"),
    role = getString("role"),
    content = getString("content"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
    authorId = optionalString("authorId"),
    authorName = optionalString("authorName"),
    authorAvatar = optionalString("authorAvatar"),
    authorColor = if (isNull("authorColor")) null else optLong("authorColor"),
    authorImage = optionalString("authorImage"),
    reasoning = optionalString("reasoning")
)

private fun JSONObject.toPost() = SpacePost(
    id = getString("id"),
    personaId = optionalString("personaId"),
    authorName = getString("authorName"),
    avatar = optString("avatar", "我"),
    content = getString("content"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
    likes = optInt("likes", 0),
    liked = optBoolean("liked", false),
    authorType = optString("authorType", "user"),
    comments = optJSONArray("comments")?.mapObjects { it.toComment() } ?: emptyList()
)

private fun JSONObject.toComment() = PostComment(
    id = getString("id"),
    postId = optString("postId", ""),
    authorName = getString("authorName"),
    avatar = optString("avatar", "我"),
    content = getString("content"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
    authorType = optString("authorType", "user")
)

private fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

/** 尚未同步到后端的本地评论（含离线生成、直连模型生成的 AI 评论） */
private fun PostComment.isPendingLocal(): Boolean =
    id.startsWith("local-") || id.startsWith("ai-local-")

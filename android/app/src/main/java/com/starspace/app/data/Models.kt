package com.starspace.app.data

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UserAccount(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val accountName: String,
    val passwordHash: String,
    val avatar: String = "我",
    val color: Long = 0xFF4AAE9B,
    val avatarImage: String? = null,
    val bio: String = "把日常和灵感，安放在自己的小宇宙"
)

data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: String = "星",
    val color: Long = 0xFF5B8DEF,
    val avatarImage: String? = null,
    val prompt: String,
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.8,
    val autoPost: Boolean = false,
    val minPostHours: Int = 4,
    val maxPostHours: Int = 12,
    val bio: String = "一个住在星屿里的 AI 朋友"
)

data class GroupChat(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: String = "群",
    val color: Long = 0xFF52748F,
    val avatarImage: String? = null,
    val members: List<String>
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val personaId: String,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val authorId: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val authorColor: Long? = null,
    val authorImage: String? = null,
    val reasoning: String? = null
)

data class SpacePost(
    val id: String = UUID.randomUUID().toString(),
    val personaId: String? = null,
    val authorName: String,
    val avatar: String = "我",
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val liked: Boolean = false,
    val authorType: String = "user",
    val comments: List<PostComment> = emptyList()
)

data class PostComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val authorName: String,
    val avatar: String = "我",
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val authorType: String = "user"
)

data class LLMConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val directMode: Boolean = true,
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val defaultModel: String = "gpt-4o-mini",
    val connected: Boolean = false
)

data class AppSettings(
    val serverUrl: String = "",
    val serverConfigured: Boolean = false,
    val tabletMode: Boolean = false,
    val darkMode: String = "system",
    val notificationsEnabled: Boolean = true,
    val llmConfigs: List<LLMConfig> = emptyList(),
    val activeConfigId: String = ""
) {
    /** 后端是否真的可用：必须由用户显式保存过地址，避免默认值让真机反复连接超时 */
    val backendReady: Boolean
        get() = serverConfigured && serverUrl.isNotBlank()

    val activeConfig: LLMConfig
        get() = llmConfigs.find { it.id == activeConfigId }
            ?: llmConfigs.firstOrNull()
            ?: EMPTY_LLM_CONFIG
}

val EMPTY_LLM_CONFIG = LLMConfig(
    id = "none",
    name = "未配置",
    directMode = true,
    baseUrl = "https://api.deepseek.com/v1",
    defaultModel = "deepseek-flash",
    connected = false
)

object ModelCatalog {
    val groups: List<Pair<String, List<String>>> = listOf(
        "OpenAI" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o3-mini"),
        "DeepSeek" to listOf("deepseek-flash", "deepseek-v4-pro"),
        "通义千问" to listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long"),
        "Kimi 月之暗面" to listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        "智谱 GLM" to listOf("glm-4-plus", "glm-4-air", "glm-4-flash"),
        "Claude（兼容网关）" to listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
        "Gemini（兼容网关）" to listOf("gemini-2.0-flash", "gemini-1.5-pro")
    )

    val all: List<String> = groups.flatMap { it.second }
}

data class AppData(
    val user: UserAccount? = null,
    val savedAccount: UserAccount? = null,
    val personas: List<Persona>,
    val groups: List<GroupChat>,
    val messages: List<ChatMessage>,
    val posts: List<SpacePost>,
    val settings: AppSettings = AppSettings()
)

fun hashPassword(password: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

fun Long.displayTime(): String {
    val now = System.currentTimeMillis()
    val pattern = if (now - this < 24 * 60 * 60 * 1000) "HH:mm" else "MM-dd"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(this))
}

fun sampleData(): AppData {
    val lin = Persona(
        id = "persona-lin",
        name = "林一",
        avatar = "林",
        color = 0xFF6A8EEB,
        prompt = "你叫林一，是用户温柔、真诚的朋友。说话自然简短，会分享音乐、晚霞和日常生活，不要反复强调自己是 AI。",
        autoPost = true,
        minPostHours = 6,
        maxPostHours = 18,
        bio = "晚风收藏家 · 喜欢音乐和散步"
    )
    val mori = Persona(
        id = "persona-mori",
        name = "Mori",
        avatar = "M",
        color = 0xFF7C68D9,
        prompt = "你叫 Mori，是理性又有点幽默的产品搭子。擅长拆解问题、头脑风暴，也会用轻松的方式表达自己的看法。",
        model = "gpt-4o-mini",
        temperature = 0.7,
        autoPost = true,
        minPostHours = 8,
        maxPostHours = 24,
        bio = "灵感练习生 · 偶尔认真，经常有趣"
    )
    val group = GroupChat(
        id = "group-demo",
        name = "星屿茶话会",
        avatar = "茶",
        color = 0xFF4AAE9B,
        members = listOf(lin.id, mori.id)
    )
    val messages = listOf(
        ChatMessage(personaId = lin.id, role = "assistant", content = "今天的云很好看，给你留了一片。"),
        ChatMessage(personaId = lin.id, role = "user", content = "那你今天做什么了？"),
        ChatMessage(personaId = lin.id, role = "assistant", content = "整理了一份散步歌单，等你来了就放给你听。"),
        ChatMessage(personaId = mori.id, role = "assistant", content = "想到一个有趣的功能：让每条动态都有一种天气。要不要一起想想？"),
        ChatMessage(
            personaId = group.id,
            role = "assistant",
            content = "群聊已建好，可以先聊聊今晚想做什么。",
            authorId = mori.id,
            authorName = mori.name,
            authorAvatar = mori.avatar,
            authorColor = mori.color
        )
    )
    val posts = listOf(
        SpacePost(
            id = "demo-post-1",
            personaId = lin.id,
            authorName = lin.name,
            avatar = lin.avatar,
            content = "今天的晚霞像一颗慢慢融化的橘子糖。把这一刻存进星屿，也分享给你。",
            createdAt = System.currentTimeMillis() - 42 * 60 * 1000,
            likes = 12,
            authorType = "ai"
        ),
        SpacePost(
            id = "demo-post-2",
            personaId = mori.id,
            authorName = mori.name,
            avatar = mori.avatar,
            content = "灵感不一定在正襟危坐时出现。有时候，它只是散步时突然撞到脑门上。",
            createdAt = System.currentTimeMillis() - 3 * 60 * 60 * 1000,
            likes = 8,
            authorType = "ai"
        )
    )
    return AppData(
        user = null,
        personas = listOf(lin, mori),
        groups = listOf(group),
        messages = messages,
        posts = posts
    )
}

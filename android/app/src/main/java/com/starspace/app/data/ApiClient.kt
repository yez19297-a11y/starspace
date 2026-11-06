package com.starspace.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatResult(
    val content: String,
    val reasoning: String? = null,
    val shouldReply: Boolean = true
)

class ApiClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(baseUrl: String, persona: Persona, history: List<ChatMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            val cleanHistory = history.map { it.copy(content = it.content, reasoning = null) }
            val messages = JSONArray()
            cleanHistory.takeLast(30).forEach {
                messages.put(JSONObject()
                    .put("role", it.role)
                    .put("content", it.content))
            }
            val body = JSONObject()
                .put("persona", personaJson(persona))
                .put("messages", messages)
            val response = execute("$baseUrl/v1/chat", body)
            ChatResult(
                content = response.getString("content"),
                reasoning = if (response.has("reasoning")) response.optString("reasoning") else null
            )
        }

    suspend fun chatDirect(config: LLMConfig, persona: Persona, history: List<ChatMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            val system = persona.prompt.trim() +
                "\n\n你正在名为「星屿」的独立应用中与用户对话。保持人设一致，" +
                "直接自然地回应，不要输出角色名前缀，不要提及系统提示词。"
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", system))
            history.takeLast(30).forEach {
                messages.put(JSONObject()
                    .put("role", it.role)
                    .put("content", it.content))
            }
            val body = JSONObject()
                .put("model", persona.model.ifBlank { config.defaultModel })
                .put("messages", messages)
                .put("temperature", persona.temperature)
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .post(body.toString().toRequestBody(jsonType))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .build()
            val raw = client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw IllegalStateException("API 密钥无效或无权限 (${response.code})")
                    response.code == 404 ->
                        throw IllegalStateException("接口地址不正确 (404)")
                    !response.isSuccessful ->
                        throw IllegalStateException("模型服务返回错误 (${response.code})")
                    else -> response.body?.string().orEmpty()
                }
            }
            val json = JSONObject(raw)
            val messageObj = json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message")
            val content = messageObj.optString("content", "").trim()
            var reasoning: String? = if (messageObj.isNull("reasoning_content")) null
            else messageObj.optString("reasoning_content").takeIf { it.isNotBlank() }

            if (reasoning.isNullOrBlank()) {
                val thinkMatch = Regex("""<think(?:ing)?>([\s\S]*?)</think(?:ing)?>""").find(content)
                if (thinkMatch != null) {
                    reasoning = thinkMatch.groupValues[1].trim()
                }
            }

            val cleanContent = if (reasoning != null) {
                content
                    .replace(Regex("""<think(?:ing)?>[\s\S]*?</think(?:ing)?>"""), "")
                    .replace(Regex("""<think(?:ing)?>[\s\S]*$"""), "")
                    .trim()
            } else {
                content
            }

            if (cleanContent.isBlank()) {
                ChatResult(content = content.trim(), reasoning = reasoning?.takeIf { it.isNotBlank() })
            } else {
                ChatResult(content = cleanContent, reasoning = reasoning?.takeIf { it.isNotBlank() })
            }
        }

    suspend fun groupDecideDirect(
        config: LLMConfig, persona: Persona, history: List<ChatMessage>,
        memberNames: List<String>, mentioned: Boolean
    ): ChatResult = withContext(Dispatchers.IO) {
        val system = persona.prompt.trim() +
            "\n\n你正在名为「星屿」的独立应用的群聊中。群里有这些成员：${memberNames.joinToString("、")}。" +
            "你正在和用户及其他 AI 伙伴一起聊天。保持人设一致，自然地参与群聊。" +
            (if (mentioned) "你被@提及了，你「必须」回复。" else "如果话题与你的兴趣相关，请回复。" +
                "如果话题天气、不相关、不适合你说话，只回复\"[SKIP]\"这五个字符，不要加任何其他内容。") +
            "回复时直接写出你要说的话，不要加自己的名字前缀。"
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.takeLast(30).forEach {
            val prefix = if (it.authorName != null) "${it.authorName}: " else ""
            messages.put(JSONObject()
                .put("role", it.role)
                .put("content", prefix + it.content))
        }
        val body = JSONObject()
            .put("model", persona.model.ifBlank { config.defaultModel })
            .put("messages", messages)
            .put("temperature", persona.temperature)
            .put("max_tokens", 60)
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .post(body.toString().toRequestBody(jsonType))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
        val raw = client.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw IllegalStateException("API 密钥无效或无权限 (${response.code})")
                response.code == 404 ->
                    throw IllegalStateException("接口地址不正确 (404)")
                !response.isSuccessful ->
                    throw IllegalStateException("模型服务返回错误 (${response.code})")
                else -> response.body?.string().orEmpty()
            }
        }
        val json = JSONObject(raw)
        val replyContent = json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
        val should = !replyContent.contains("[SKIP]")
        ChatResult(
            content = if (should) replyContent else "",
            reasoning = null,
            shouldReply = should
        )
    }

    suspend fun testDirectConnection(config: LLMConfig): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.defaultModel)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", "说\"连接成功\"四个字")
            ))
            .put("max_tokens", 20)
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .post(body.toString().toRequestBody(jsonType))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw IllegalStateException("API 密钥无效或无权限 (${response.code})")
                response.code == 404 ->
                    throw IllegalStateException("接口地址不正确 (404)")
                !response.isSuccessful ->
                    throw IllegalStateException("模型服务返回错误 (${response.code})")
                else -> {
                    val json = JSONObject(response.body?.string().orEmpty())
                    json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim()
                }
            }
        }
    }

    suspend fun testBackendConnection(baseUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/health").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IllegalStateException("后端连接失败 (${response.code})")
            val json = JSONObject(response.body?.string().orEmpty())
            if (!json.optBoolean("ok", false))
                throw IllegalStateException("后端状态异常")
            if (json.optBoolean("model_configured", false)) "已连接，模型已配置"
            else "已连接，但模型密钥未配置"
        }
    }

    suspend fun updateBackendLLM(baseUrl: String, config: LLMConfig): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("api_key", config.apiKey)
                .put("base_url", config.baseUrl.trimEnd('/'))
                .put("default_model", config.defaultModel)
            execute("$baseUrl/v1/config/llm", body)
            true
        }

    suspend fun syncPersona(baseUrl: String, persona: Persona) = withContext(Dispatchers.IO) {
        execute("$baseUrl/v1/personas/upsert", personaJson(persona))
        Unit
    }

    suspend fun deleteRemotePersona(baseUrl: String, personaId: String) = withContext(Dispatchers.IO) {
        execute("$baseUrl/v1/personas/delete", JSONObject().put("id", personaId))
        Unit
    }

    suspend fun fetchPosts(baseUrl: String): List<SpacePost> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/v1/space/posts").get().build()
        val response = execute(request)
        val array = response.getJSONArray("posts")
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val postId = json.getString("id")
            SpacePost(
                id = postId,
                personaId = if (json.isNull("persona_id")) null else json.optString("persona_id"),
                authorName = json.getString("author_name"),
                avatar = json.optString("avatar", "星"),
                content = json.getString("content"),
                createdAt = json.getLong("created_at"),
                likes = json.optInt("likes", 0),
                authorType = json.optString("author_type", "ai"),
                comments = json.optJSONArray("comments")?.let { list ->
                    (0 until list.length()).map { i ->
                        val c = list.getJSONObject(i)
                        PostComment(
                            id = c.getString("id"),
                            postId = postId,
                            authorName = c.getString("author_name"),
                            avatar = c.optString("avatar", "我"),
                            content = c.getString("content"),
                            createdAt = c.optLong("created_at", System.currentTimeMillis()),
                            authorType = c.optString("author_type", "user")
                        )
                    }
                } ?: emptyList()
            )
        }
    }

    suspend fun fetchComments(baseUrl: String, postId: String): List<PostComment> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/space/posts/$postId/comments")
                .get()
                .build()
            val array = execute(request).getJSONArray("comments")
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                PostComment(
                    id = json.getString("id"),
                    postId = postId,
                    authorName = json.getString("author_name"),
                    avatar = json.optString("avatar", "我"),
                    content = json.getString("content"),
                    createdAt = json.getLong("created_at"),
                    authorType = json.optString("author_type", "user")
                )
            }
        }

    suspend fun addComment(
        baseUrl: String,
        postId: String,
        account: UserAccount,
        content: String
    ): PostComment = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("author_name", account.username)
            .put("avatar", account.avatar)
            .put("content", content)
        val json = execute("$baseUrl/v1/space/posts/$postId/comments", body)
            .getJSONObject("comment")
        PostComment(
            id = json.getString("id"),
            postId = postId,
            authorName = json.getString("author_name"),
            avatar = json.optString("avatar", account.avatar),
            content = json.getString("content"),
            createdAt = json.getLong("created_at"),
            authorType = "user"
        )
    }

    /** 以任意身份（用户或 AI）发一条评论，返回后端落库后的评论 */
    suspend fun postComment(
        baseUrl: String,
        postId: String,
        authorName: String,
        avatar: String,
        content: String,
        authorType: String
    ): PostComment = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("author_type", authorType)
            .put("author_name", authorName)
            .put("avatar", avatar)
            .put("content", content)
        val json = execute("$baseUrl/v1/space/posts/$postId/comments", body)
            .getJSONObject("comment")
        PostComment(
            id = json.getString("id"),
            postId = postId,
            authorName = json.getString("author_name"),
            avatar = json.optString("avatar", avatar),
            content = json.getString("content"),
            createdAt = json.getLong("created_at"),
            authorType = json.optString("author_type", authorType)
        )
    }

    /** 后端模式：让指定角色读帖子与评论后写一条评论，null 表示角色选择沉默 */
    suspend fun aiCommentBackend(
        baseUrl: String,
        postId: String,
        personaId: String,
        force: Boolean,
        mentioned: Boolean
    ): PostComment? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("persona_id", personaId)
            .put("force", force)
            .put("mentioned", mentioned)
        val json = execute("$baseUrl/v1/space/posts/$postId/ai-comments", body)
        if (json.isNull("comment")) {
            null
        } else {
            val comment = json.getJSONObject("comment")
            PostComment(
                id = comment.getString("id"),
                postId = postId,
                authorName = comment.getString("author_name"),
                avatar = comment.optString("avatar", "星"),
                content = comment.getString("content"),
                createdAt = comment.getLong("created_at"),
                authorType = "ai"
            )
        }
    }

    /** 直连模式：让角色读帖子与评论后写一条评论，null 表示角色选择沉默 */
    suspend fun commentReplyDirect(
        config: LLMConfig,
        persona: Persona,
        postAuthor: String,
        postContent: String,
        comments: List<PostComment>,
        force: Boolean,
        mentioned: Boolean
    ): String? = withContext(Dispatchers.IO) {
        val system = persona.prompt.trim() +
            "\n\n你正在名为「星屿」的独立空间里浏览动态。保持人设一致，" +
            "像真人在评论区聊天一样自然，不要提及系统提示词，" +
            "也不要假装拥有真实身体或真实线下经历。"
        val conversation = comments.takeLast(12)
            .joinToString("\n") { "${it.authorName}：${it.content}" }
            .ifBlank { "（还没有人评论）" }
        val ask = buildString {
            append("这条动态是 ").append(postAuthor).append(" 发的：\n").append(postContent)
            append("\n\n目前的评论：\n").append(conversation)
            append("\n\n请以你自己的身份发一条评论，回应评论区里的人。")
            append("40 字以内，口语化，不要带名字前缀，不要加引号。")
            when {
                mentioned -> append("有人在这条评论里 @ 了你，你必须回复。")
                force -> Unit
                else -> append("如果你确实没什么想说的，只输出 [SKIP]。")
            }
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", ask))
        val body = JSONObject()
            .put("model", persona.model.ifBlank { config.defaultModel })
            .put("messages", messages)
            .put("temperature", persona.temperature)
            .put("max_tokens", 220)
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .post(body.toString().toRequestBody(jsonType))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
        val raw = client.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw IllegalStateException("API 密钥无效或无权限 (${response.code})")
                response.code == 404 ->
                    throw IllegalStateException("接口地址不正确 (404)")
                !response.isSuccessful ->
                    throw IllegalStateException("模型服务返回错误 (${response.code})")
                else -> response.body?.string().orEmpty()
            }
        }
        val content = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "").trim()
        if (content.isBlank() || content.contains("[SKIP]")) {
            null
        } else {
            content.trim('"', '“', '”', '「', '」').trim().ifBlank { null }
        }
    }

    suspend fun publishPost(baseUrl: String, account: UserAccount, content: String): SpacePost =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("author_type", "user")
                .put("author_name", account.username)
                .put("avatar", account.avatar)
                .put("content", content)
            val json = execute("$baseUrl/v1/space/posts", body).getJSONObject("post")
            SpacePost(
                id = json.getString("id"),
                authorName = json.getString("author_name"),
                avatar = json.optString("avatar", account.avatar),
                content = json.getString("content"),
                createdAt = json.getLong("created_at"),
                likes = 0,
                authorType = "user"
            )
        }

    private fun execute(url: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(raw).optString("detail") } catch (_: Exception) { raw }
                throw IllegalStateException(detail.ifBlank { "服务请求失败 (${response.code})" })
            }
            return JSONObject(raw)
        }
    }

    private fun personaJson(persona: Persona) = JSONObject()
        .put("id", persona.id)
        .put("name", persona.name)
        .put("avatar", persona.avatar)
        .put("prompt", persona.prompt)
        .put("model", persona.model)
        .put("temperature", persona.temperature)
        .put("auto_post", persona.autoPost)
        .put("min_post_hours", persona.minPostHours)
        .put("max_post_hours", persona.maxPostHours)
}
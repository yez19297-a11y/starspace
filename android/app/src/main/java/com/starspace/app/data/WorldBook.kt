package com.starspace.app.data

/**
 * 世界书的匹配与注入。
 *
 * 规则很轻：拿群里最近几条消息拼成一段文本，逐条比对关键词，命中就把设定拼到
 * system prompt 里。没有向量检索，也不需要联网——纯本地字符串匹配，够快也够可控。
 */
object WorldBook {
    /** 注入到提示词的设定上限，避免把上下文撑爆 */
    private const val MAX_PROMPT_CHARS = 1800

    /** 只扫最近这么多条消息，太早的旧话题不该持续触发 */
    private const val SCAN_MESSAGES = 10

    /** 关键词支持中英文逗号、顿号、分号和换行分隔 */
    fun splitKeywords(raw: String): List<String> =
        raw.split(',', '，', '、', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /** 命中条目拼成的正文，没有命中时返回空串 */
    fun collect(entries: List<WorldBookEntry>, messages: List<ChatMessage>): String {
        val candidates = entries.filter { it.enabled && it.content.isNotBlank() }
        if (candidates.isEmpty()) return ""

        val recent = messages.takeLast(SCAN_MESSAGES)
            .joinToString("\n") { message -> message.content }
            .lowercase()

        val builder = StringBuilder()
        var used = 0
        for (entry in candidates) {
            val hit = entry.keywords.isEmpty() ||
                entry.keywords.any { keyword -> recent.contains(keyword.lowercase()) }
            if (!hit) continue

            val block = "【${entry.title.ifBlank { "设定" }}】${entry.content.trim()}"
            if (used + block.length + 1 > MAX_PROMPT_CHARS) {
                val remain = MAX_PROMPT_CHARS - used
                if (remain > 40) {
                    builder.append(block.take(remain)).append('\n')
                }
                break
            }
            builder.append(block).append('\n')
            used += block.length + 1
        }
        return builder.toString().trim()
    }

    /** 包装成可直接拼进 system prompt 的段落 */
    fun promptBlock(entries: List<WorldBookEntry>, messages: List<ChatMessage>): String {
        val body = collect(entries, messages)
        if (body.isBlank()) return ""
        return "\n\n【世界书设定】下面是与当前对话相关的背景设定，回复时必须与之一致；" +
            "不要在回复里逐条复述，也不要提到「世界书」这个词：\n$body"
    }
}

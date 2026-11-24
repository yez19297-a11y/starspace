package com.starspace.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.Inflater

/**
 * 酒馆（SillyTavern）角色卡与世界书的导入。
 *
 * 角色卡支持：
 *  - PNG 角色卡：JSON 以 base64 编码塞进 PNG 的 tEXt / iTXt 块（关键字 chara / ccv3）
 *  - JSON 角色卡：v2 规范的 {"spec": ..., "data": {...}}，或者扁平字段的 v1
 * 世界书支持酒馆的 world info JSON（entries 可以是对象或数组），
 * 角色卡自带的 character_book 也会一起导进来。
 *
 * 全部离线完成，不联网、不依赖第三方库。
 */
object TavernImport {
    /** prompt 上限，防止一张卡把上下文撑爆 */
    private const val MAX_PROMPT_CHARS = 6000

    /** 单次导入的世界书条目上限 */
    private const val MAX_ENTRIES = 200

    data class CardImport(
        val persona: Persona,
        val worldBook: List<WorldBookEntry> = emptyList()
    )

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        for (index in 0 until 8) {
            if (bytes[index] != signature[index]) return false
        }
        return true
    }

    /** 解析角色卡；不是角色卡时返回 null */
    fun parseCard(bytes: ByteArray, fallbackName: String = "导入的角色"): CardImport? {
        val text = if (isPng(bytes)) {
            val payload = readPngPayload(bytes) ?: return null
            String(payload, Charsets.UTF_8)
        } else {
            String(bytes, Charsets.UTF_8)
        }
        val root = runCatching { JSONObject(text.trim().removePrefix("\uFEFF")) }.getOrNull()
            ?: return null
        return cardOf(root, fallbackName)
    }

    /** 解析酒馆世界书 JSON；失败时返回空列表 */
    fun parseWorldBook(text: String): List<WorldBookEntry> {
        val raw = text.trim().removePrefix("\uFEFF")
        if (raw.isBlank()) return emptyList()
        val root: Any = runCatching { JSONObject(raw) }.getOrNull()
            ?: runCatching { JSONArray(raw) }.getOrNull()
            ?: return emptyList()
        val node = if (root is JSONObject && root.has("entries")) {
            root.opt("entries") ?: return emptyList()
        } else {
            root
        }
        return entriesOf(node)
    }

    /** 把角色卡里的 PNG 存成头像文件，返回绝对路径 */
    fun saveAvatar(context: Context, bytes: ByteArray): String? = runCatching {
        val directory = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    // ------------------------------------------------------------ 角色卡

    private fun cardOf(root: JSONObject, fallbackName: String): CardImport? {
        val data = root.optJSONObject("data") ?: root
        val name = data.optString("name").trim().ifBlank { fallbackName }.take(40)

        val sections = listOf(
            "角色描述" to data.optString("description"),
            "性格" to data.optString("personality"),
            "场景" to data.optString("scenario"),
            "扮演要求" to data.optString("system_prompt"),
            "对话示例" to data.optString("mes_example"),
            "补充设定" to data.optString("post_history_instructions")
        ).map { it.first to it.second.trim() }
            .filter { it.second.isNotBlank() }

        val prompt = if (sections.isEmpty()) {
            "你是「$name」，一个住在星屿里的 AI 伙伴。用自然、真诚的方式和用户聊天。"
        } else {
            sections.joinToString("\n\n") { "【${it.first}】\n${it.second}" }
                .take(MAX_PROMPT_CHARS)
        }

        val bio = data.optString("creator_notes").trim()
            .ifBlank { data.optString("personality").trim() }
            .ifBlank { "从酒馆角色卡导入" }
            .lineSequence()
            .first()
            .take(60)

        return CardImport(
            persona = Persona(
                name = name,
                avatar = name.take(1).ifBlank { "星" },
                prompt = prompt,
                bio = bio
            ),
            worldBook = entriesOf(data.opt("character_book"))
        )
    }

    // ------------------------------------------------------------ 世界书

    private fun entriesOf(node: Any?): List<WorldBookEntry> {
        val out = mutableListOf<WorldBookEntry>()
        when (node) {
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    val item = node.optJSONObject(index) ?: continue
                    out += parseEntry(item, out.size) ?: continue
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    when (val child = node.opt(keys.next())) {
                        is JSONObject -> {
                            if (child.has("content")) {
                                out += parseEntry(child, out.size) ?: continue
                            } else {
                                out += entriesOf(child)
                            }
                        }
                        is JSONArray -> out += entriesOf(child)
                    }
                }
            }
        }
        return out.take(MAX_ENTRIES)
    }

    private fun parseEntry(json: JSONObject, index: Int): WorldBookEntry? {
        val content = json.optString("content").trim()
        if (content.isBlank()) return null

        val keywords = mutableListOf<String>()
        fun collect(value: Any?) {
            when (value) {
                is JSONArray -> {
                    for (i in 0 until value.length()) keywords += value.optString(i)
                }
                is String -> keywords += value.split(',', '，', '、')
            }
        }
        collect(json.opt("key"))
        collect(json.opt("keys"))
        collect(json.opt("keywords"))
        collect(json.opt("keysecondary"))

        val keys = keywords.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(16)

        // constant = 常驻条目，按星屿的约定就是「关键词留空」
        val constant = json.optBoolean("constant", false)
        val enabled = json.optBoolean("enabled", true) &&
            !json.optBoolean("disable", false) &&
            !json.optBoolean("disabled", false)

        val title = json.optString("comment").trim()
            .ifBlank { json.optString("name").trim() }
            .ifBlank { keys.firstOrNull().orEmpty() }
            .ifBlank { "导入设定 ${index + 1}" }

        return WorldBookEntry(
            id = UUID.randomUUID().toString(),
            title = title.take(60),
            keywords = if (constant) emptyList() else keys,
            content = content.take(4000),
            enabled = enabled
        )
    }

    // ------------------------------------------------------------ PNG 文本块

    private fun readPngPayload(bytes: ByteArray): ByteArray? {
        var position = 8
        while (position + 8 <= bytes.size) {
            val length = readInt(bytes, position)
            if (length < 0 || position + 12 + length > bytes.size) return null
            val type = String(bytes, position + 4, 4, Charsets.US_ASCII)
            val start = position + 8
            val data = bytes.copyOfRange(start, start + length)
            val payload = when (type) {
                "tEXt" -> decodeTextChunk(data)
                "iTXt" -> decodeInternationalTextChunk(data)
                else -> null
            }
            if (payload != null) return payload
            if (type == "IEND") return null
            position = start + length + 4
        }
        return null
    }

    private fun decodeTextChunk(data: ByteArray): ByteArray? {
        val zero = data.indexOf(0.toByte())
        if (zero <= 0) return null
        val keyword = String(data, 0, zero, Charsets.ISO_8859_1).lowercase()
        if (keyword != "chara" && keyword != "ccv3") return null
        return decodeBase64(String(data, zero + 1, data.size - zero - 1, Charsets.ISO_8859_1))
    }

    private fun decodeInternationalTextChunk(data: ByteArray): ByteArray? {
        var index = 0

        fun readZeroTerminated(): String {
            val start = index
            while (index < data.size && data[index] != 0.toByte()) index++
            val text = String(data, start, index - start, Charsets.UTF_8)
            if (index < data.size) index++
            return text
        }

        val keyword = readZeroTerminated().lowercase()
        if (keyword != "chara" && keyword != "ccv3") return null
        if (index + 2 > data.size) return null
        val compressed = data[index].toInt() != 0
        index += 2
        readZeroTerminated() // 语言标签
        readZeroTerminated() // 翻译后的关键字
        if (index > data.size) return null
        val body = data.copyOfRange(index, data.size)
        val text = if (compressed) {
            runCatching { inflate(body) }.getOrNull() ?: return null
        } else {
            body
        }
        return decodeBase64(String(text, Charsets.UTF_8))
    }

    private fun decodeBase64(text: String): ByteArray? =
        runCatching { Base64.decode(text, Base64.DEFAULT) }.getOrNull()

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            output.write(buffer, 0, count)
        }
        inflater.end()
        return output.toByteArray()
    }
}

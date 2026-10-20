package cn.lesson.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

object BackupCodec {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    fun encode(state: AppState): String = gson.toJson(state)
    fun decode(json: String): AppState {
        require(json.length <= 20 * 1024 * 1024) { "备份文件过大" }
        try {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("schemaVersion")?.asInt == 1) { "这不是兼容的课表备份文件" }
            require(listOf("term", "schedule", "todos", "history", "preferences").all { root.has(it) && !root[it].isJsonNull }) { "备份缺少必要内容" }
            return gson.fromJson(root, AppState::class.java).also { it.validate() }
        } catch (e: Exception) {
            throw IllegalArgumentException("无法读取备份：${e.message ?: "文件内容不完整"}", e)
        }
    }
}

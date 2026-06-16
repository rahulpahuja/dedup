package com.rp.dedup.feature.voicestorage.data.repository

import android.content.Context
import com.rp.dedup.feature.voicestorage.presentation.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

class ChatHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("dedup_chat_history", Context.MODE_PRIVATE)

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_STORED).forEach { msg ->
            array.put(JSONObject().apply {
                put("id", msg.id)
                put("text", msg.text)
                put("isUser", msg.isUser)
                put("timestamp", msg.timestamp)
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun load(): List<ChatMessage> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ChatMessage(
                    id        = obj.getString("id"),
                    text      = obj.getString("text"),
                    isUser    = obj.getBoolean("isUser"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val KEY = "messages"
        private const val MAX_STORED = 200
    }
}

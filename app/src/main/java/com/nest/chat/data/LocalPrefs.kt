package com.nest.chat.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class LocalPrefs(context: Context) {

    private val sp = context.getSharedPreferences("nest_local", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(value) = sp.edit().putInt("theme", value).apply()

    var blockScreenshots: Boolean
        get() = sp.getBoolean("secure", false)
        set(value) = sp.edit().putBoolean("secure", value).apply()

    var bubbleX: Float
        get() = sp.getFloat("bubble_x", 1f)
        set(value) = sp.edit().putFloat("bubble_x", value).apply()

    var bubbleY: Float
        get() = sp.getFloat("bubble_y", 0.72f)
        set(value) = sp.edit().putFloat("bubble_y", value).apply()

    val hasPin: Boolean
        get() = !sp.getString("pin_hash", "").isNullOrEmpty()

    val pinLength: Int
        get() = sp.getInt("pin_len", 4)

    fun setPin(pin: String) {
        val saltBytes = ByteArray(12)
        SecureRandom().nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        sp.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash(salt, pin))
            .putInt("pin_len", pin.length)
            .apply()
    }

    fun clearPin() {
        sp.edit().remove("pin_salt").remove("pin_hash").remove("pin_len").apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pin_salt", "").orEmpty()
        val stored = sp.getString("pin_hash", "").orEmpty()
        return stored.isNotEmpty() && hash(salt, pin) == stored
    }

    fun openedAt(chatId: String): Long = sp.getLong("open_$chatId", 0L)

    fun setOpenedAt(chatId: String, time: Long) {
        sp.edit().putLong("open_$chatId", time).apply()
    }

    fun allOpened(): Map<String, Long> {
        val out = HashMap<String, Long>()
        for ((key, value) in sp.all) {
            if (key.startsWith("open_") && value is Long) out[key.removePrefix("open_")] = value
        }
        return out
    }

    fun draft(chatId: String): String = sp.getString("draft_$chatId", "").orEmpty()

    fun setDraft(chatId: String, text: String) {
        if (text.isBlank()) sp.edit().remove("draft_$chatId").apply() else sp.edit().putString("draft_$chatId", text).apply()
    }

    fun allDrafts(): Map<String, String> {
        val out = HashMap<String, String>()
        for ((key, value) in sp.all) {
            if (key.startsWith("draft_") && value is String && value.isNotEmpty()) out[key.removePrefix("draft_")] = value
        }
        return out
    }

    fun wipe() {
        sp.edit().clear().apply()
    }

    private fun hash(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

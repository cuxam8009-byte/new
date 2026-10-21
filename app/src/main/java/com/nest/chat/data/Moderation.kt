package com.nest.chat.data

import android.graphics.Bitmap
import java.text.Normalizer

enum class ImageVerdict { SAFE, BLOCK, VIOLATION }

class Finding(val reason: String, val severe: Boolean)

class Penalty(val strikes: Int, val bannedUntil: Long, val suspended: Boolean, val label: String)

object Moderation {

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    private val accentedWords = setOf(
        "đéo", "đụ", "địt", "đĩ", "lồn", "cặc", "buồi", "cứt", "đcm", "đmm", "đkm", "đm"
    )

    private val plainWords = setOf(
        "dm", "dmm", "dcm", "dcmm", "dkm", "dmml", "vcl", "vkl", "vclm", "clgt",
        "dit", "ditme", "ditmay", "fuck", "fuk", "fck", "shit", "bitch", "cunt",
        "asshole", "motherfucker"
    )

    private val plainPhrases = setOf(
        "du ma", "du me", "dit me", "dit con me", "dit cu"
    )

    private val compactWords = listOf(
        "dumamay", "dumemay", "ditmemay", "ditconme", "motherfucker"
    )

    private val tokenSplit = Regex("[^\\p{L}]+")
    private val markStrip = Regex("\\p{Mn}+")

    fun scanText(text: String): String? {
        if (text.isBlank()) return null
        val base = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)

        val accentedTokens = base.split(tokenSplit).filter { it.isNotEmpty() }
        if (accentedTokens.any { it in accentedWords }) return "Ngôn từ tục tĩu"

        val folded = fold(base)
        val tokens = mergeSingles(folded.split(tokenSplit).filter { it.isNotEmpty() })
        if (tokens.any { it in plainWords }) return "Ngôn từ tục tĩu"

        for (i in 0 until tokens.size - 1) {
            if ("${tokens[i]} ${tokens[i + 1]}" in plainPhrases) return "Ngôn từ tục tĩu"
        }
        for (i in 0 until tokens.size - 2) {
            if ("${tokens[i]} ${tokens[i + 1]} ${tokens[i + 2]}" in plainPhrases) return "Ngôn từ tục tĩu"
        }

        val compact = tokens.joinToString("")
        if (compactWords.any { compact.contains(it) }) return "Ngôn từ tục tĩu"
        return null
    }

    private fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        var previous = ' '
        for (ch in markStrip.replace(decomposed, "")) {
            val mapped = when (ch) {
                'đ' -> 'd'
                '0' -> 'o'
                '1' -> 'i'
                '3' -> 'e'
                '4' -> 'a'
                '5' -> 's'
                '@' -> 'a'
                '$' -> 's'
                else -> ch
            }
            if (mapped.isLetter() && mapped == previous) continue
            sb.append(mapped)
            previous = mapped
        }
        return sb.toString()
    }

    private fun mergeSingles(tokens: List<String>): List<String> {
        val out = ArrayList<String>()
        val run = StringBuilder()
        var runCount = 0
        fun flush() {
            if (runCount >= 2) out.add(run.toString()) else if (runCount == 1) out.add(run.toString())
            run.setLength(0)
            runCount = 0
        }
        for (token in tokens) {
            if (token.length == 1) {
                run.append(token)
                runCount++
            } else {
                flush()
                out.add(token)
            }
        }
        flush()
        return out
    }

    fun penaltyFor(current: UserProfile, severe: Boolean, byReport: Boolean, now: Long): Penalty {
        val strikes = current.strikes + 1
        val duration = when {
            severe -> 30 * DAY
            strikes == 1 -> DAY
            strikes == 2 -> 3 * DAY
            strikes == 3 -> 7 * DAY
            else -> 30 * DAY
        }
        val suspend = current.suspended ||
            strikes >= 5 ||
            (severe && !byReport)
        val until = maxOf(current.bannedUntil, now + duration)
        val label = when {
            suspend -> "Tài khoản bị đình chỉ tạm thời"
            duration == DAY -> "Cấm chat 24 giờ"
            duration == 3 * DAY -> "Cấm chat 3 ngày"
            duration == 7 * DAY -> "Cấm chat 7 ngày"
            else -> "Cấm chat 30 ngày"
        }
        return Penalty(strikes, until, suspend, label)
    }
}

object ImageScanner {

    private const val SIDE = 96
    private const val BLOCK_RATIO = 0.55f
    private const val VIOLATION_RATIO = 0.80f

    fun scan(source: Bitmap): ImageVerdict {
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        val scale = SIDE.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        var skin = 0
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
            if (y > 50f && cb in 77f..127f && cr in 135f..173f && r > g && r > b) skin++
        }
        val ratio = skin.toFloat() / pixels.size
        return when {
            ratio >= VIOLATION_RATIO -> ImageVerdict.VIOLATION
            ratio >= BLOCK_RATIO -> ImageVerdict.BLOCK
            else -> ImageVerdict.SAFE
        }
    }
}

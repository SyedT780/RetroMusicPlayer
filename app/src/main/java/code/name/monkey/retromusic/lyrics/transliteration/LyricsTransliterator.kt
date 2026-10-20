package code.name.monkey.retromusic.lyrics.transliteration

import android.os.Build
import java.util.regex.Pattern

object LyricsTransliterator {

    private val TIMESTAMP_REGEX = Pattern.compile("^((?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{2,3})?\\])+)(.*)$")

    private val icuTransliterator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun transliterateLrc(rawLrc: String): String {
        if (rawLrc.isBlank()) return rawLrc
        val lines = rawLrc.lines()
        val builder = StringBuilder()

        for (line in lines) {
            val matcher = TIMESTAMP_REGEX.matcher(line)
            if (matcher.matches()) {
                val timestamps = matcher.group(1) ?: ""
                val text = matcher.group(2) ?: ""
                val transliteratedText = transliterateString(text)
                builder.append(timestamps).append(transliteratedText).append("\n")
            } else {
                builder.append(line).append("\n")
            }
        }
        return builder.toString().trimEnd()
    }

    fun transliterateString(input: String): String {
        if (input.isBlank() || isPureAscii(input)) return input

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && icuTransliterator != null) {
                val result = icuTransliterator!!.transliterate(input)
                if (result.isNotBlank()) result else input
            } else {
                input
            }
        } catch (e: Exception) {
            input
        }
    }

    private fun isPureAscii(input: String): Boolean {
        for (ch in input) {
            if (ch.code > 127) return false
        }
        return true
    }
}

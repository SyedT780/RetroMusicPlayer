package code.name.monkey.retromusic.lyrics.transliteration

import android.os.Build
import code.name.monkey.retromusic.lyrics.model.TransliterationResult
import java.util.regex.Pattern

object LyricsTransliterator {

    private val TIMESTAMP_REGEX = Pattern.compile("^((?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{2,3})?\\])+)(.*)$")

    private val icuTransliterator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    fun transliterateLrc(rawLrc: String): TransliterationResult {
        if (rawLrc.isBlank()) return TransliterationResult(rawLrc, false)
        
        // Quick check if entire text is pure ASCII
        val isPureAscii = isPureAsciiText(rawLrc)
        if (isPureAscii) return TransliterationResult(rawLrc, false)
        
        val lines = rawLrc.lines()
        val builder = StringBuilder()
        var wasAnyTransliterated = false

        for (line in lines) {
            val matcher = TIMESTAMP_REGEX.matcher(line)
            if (matcher.matches()) {
                val timestamps = matcher.group(1) ?: ""
                val text = matcher.group(2) ?: ""
                val (transliteratedText, wasTransliterated) = transliterateString(text)
                wasAnyTransliterated = wasAnyTransliterated || wasTransliterated
                builder.append(timestamps).append(transliteratedText).append("\n")
            } else {
                builder.append(line).append("\n")
            }
        }
        return TransliterationResult(builder.toString().trimEnd(), wasAnyTransliterated)
    }

    private fun transliterateString(input: String): Pair<String, Boolean> {
        if (input.isBlank() || isPureAscii(input)) return Pair(input, false)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && icuTransliterator != null) {
                val result = icuTransliterator!!.transliterate(input)
                if (result.isNotBlank() && result != input) {
                    Pair(result, true)
                } else {
                    Pair(input, false)
                }
            } else {
                Pair(input, false)
            }
        } catch (e: Exception) {
            Pair(input, false)
        }
    }

    private fun isPureAscii(input: String): Boolean {
        for (ch in input) {
            if (ch.code > 127) return false
        }
        return true
    }

    private fun isPureAsciiText(text: String): Boolean {
        for (ch in text) {
            if (ch.code > 127) return false
        }
        return true
    }
}

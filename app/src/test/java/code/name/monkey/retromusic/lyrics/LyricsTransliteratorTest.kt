package code.name.monkey.retromusic.lyrics

import code.name.monkey.retromusic.lyrics.transliteration.LyricsTransliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTransliteratorTest {

    @Test
    fun testTimestampPreservation() {
        val rawLrc = "[00:12.34] Hello World\n[01:23.456] This is a test"
        val output = LyricsTransliterator.transliterateLrc(rawLrc)

        assertTrue(output.contains("[00:12.34] Hello World"))
        assertTrue(output.contains("[01:23.456] This is a test"))
    }

    @Test
    fun testMultipleTimestampsPreservation() {
        val rawLrc = "[00:12.34][00:56.78] Repeated chorus"
        val output = LyricsTransliterator.transliterateLrc(rawLrc)

        assertTrue(output.contains("[00:12.34][00:56.78] Repeated chorus"))
    }

    @Test
    fun testPureAsciiPassthrough() {
        val input = "Simple English text 123!"
        val output = LyricsTransliterator.transliterateString(input)

        assertEquals(input, output)
    }

    @Test
    fun testEmptyInputHandling() {
        val output = LyricsTransliterator.transliterateLrc("")
        assertEquals("", output)
    }
}

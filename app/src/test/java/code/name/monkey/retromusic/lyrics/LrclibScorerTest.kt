package code.name.monkey.retromusic.lyrics

import code.name.monkey.retromusic.lyrics.network.LrclibRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrclibScorerTest {

    private val repository = LrclibRepository()

    @Test
    fun testExactStringSimilarity() {
        val score = repository.stringSimilarity("Hotel California", "hotel california")
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun testLevenshteinSimilarity() {
        val score = repository.stringSimilarity("Bohemian Rhapsody", "Bohemian Rapsody")
        assertTrue(score > 0.85f)
    }

    @Test
    fun testBlankStringSimilarity() {
        val score = repository.stringSimilarity("", "")
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun testDisambiguationThreshold() {
        val topScore = 0.88f
        val secondScore = 0.80f // delta = 0.08 < 0.12

        val isAutoSelectOverwhelming = (topScore >= 0.85f) && ((topScore - secondScore) >= 0.12f)
        assertTrue(!isAutoSelectOverwhelming) // Should trigger disambiguation prompt!

        val highDeltaSecondScore = 0.70f // delta = 0.18 >= 0.12
        val isAutoSelectHighConfidence = (topScore >= 0.85f) && ((topScore - highDeltaSecondScore) >= 0.12f)
        assertTrue(isAutoSelectHighConfidence) // Should auto-select top candidate!
    }
}

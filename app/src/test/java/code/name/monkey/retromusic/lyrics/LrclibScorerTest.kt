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

    @Test
    fun testSplitArtists() {
        val artists = repository.splitArtists("Sharib Toshi, Arijit Singh")
        assertEquals(2, artists.size)
        assertEquals("Sharib Toshi", artists[0])
        assertEquals("Arijit Singh", artists[1])

        val featArtists = repository.splitArtists("Song Artist feat. Secondary Artist")
        assertEquals(2, featArtists.size)
        assertEquals("Song Artist", featArtists[0])
        assertEquals("Secondary Artist", featArtists[1])
    }

    @Test
    fun testCleanTitle() {
        val cleaned1 = repository.cleanTitle("Saanson Ko (Official Video)")
        assertEquals("Saanson Ko", cleaned1)

        val cleaned2 = repository.cleanTitle("Hotel California - 2013 Remaster")
        assertEquals("Hotel California", cleaned2)
    }

    @Test
    fun testMultiArtistSimilarity() {
        val sim = repository.calculateArtistSimilarity("Sharib Toshi, Arijit Singh", "Arijit Singh")
        assertEquals(1.0f, sim, 0.001f)
    }
}

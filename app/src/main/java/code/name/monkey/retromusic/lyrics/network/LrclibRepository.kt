package code.name.monkey.retromusic.lyrics.network

import code.name.monkey.retromusic.lyrics.model.CandidateRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max

class LrclibRepository {
    private val baseUrl = "https://lrclib.net"
    private val userAgent = "MusicAppLyricsStudio/3.0 (Android)"
    private val rateLimiterMutex = Mutex()
    private var lastRequestTime = 0L
    private val minDelayMs = 350L

    private suspend fun throttle() {
        rateLimiterMutex.withLock {
            val now = System.currentTimeMillis()
            val diff = now - lastRequestTime
            if (diff < minDelayMs) {
                delay(minDelayMs - diff)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    suspend fun findCandidates(
        title: String,
        artist: String,
        album: String,
        durationSec: Double
    ): List<CandidateRecord> {
        val rawResults = mutableListOf<JSONObject>()

        // Step 1: Strict /api/get
        throttle()
        val getUrl = "$baseUrl/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}&album_name=${enc(album)}&duration=${durationSec.toInt()}"
        val getRes = httpGet(getUrl)
        if (getRes != null && getRes.optString("syncedLyrics").isNotBlank()) {
            rawResults.add(getRes)
        }

        // Step 2: Fallback /api/search
        throttle()
        val searchUrl = "$baseUrl/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val searchArray = httpGetArray(searchUrl)
        if (searchArray != null) {
            for (i in 0 until searchArray.length()) {
                val item = searchArray.getJSONObject(i)
                if (item.optString("syncedLyrics").isNotBlank()) {
                    rawResults.add(item)
                }
            }
        }

        // Step 3: Weighted Prioritized Scoring (Title > Album > Artist > Duration)
        val candidateMap = mutableMapOf<Long, CandidateRecord>()
        for (obj in rawResults) {
            val id = obj.optLong("id")
            if (candidateMap.containsKey(id)) continue

            val cTitle = obj.optString("trackName")
            val cArtist = obj.optString("artistName")
            val cAlbum = obj.optString("albumName")
            val cDur = obj.optDouble("duration", 0.0)
            val cLyrics = obj.optString("syncedLyrics")

            val sTitle = stringSimilarity(title, cTitle)
            val sAlbum = if (album.isNotBlank() && cAlbum.isNotBlank()) stringSimilarity(album, cAlbum) else 0.5f
            val sArtist = if (artist.isNotBlank() && cArtist.isNotBlank()) stringSimilarity(artist, cArtist) else 0.5f
            val durDelta = abs(durationSec - cDur)
            val sDuration = max(0.0, 1.0 - (durDelta / 5.0)).toFloat()

            // Formula: Song (0.50) > Album (0.25) > Artist (0.15) > Duration (0.10)
            val compositeScore = (0.50f * sTitle) + (0.25f * sAlbum) + (0.15f * sArtist) + (0.10f * sDuration)

            if (compositeScore >= 0.60f) {
                candidateMap[id] = CandidateRecord(
                    id = id,
                    trackName = cTitle,
                    artistName = cArtist,
                    albumName = cAlbum,
                    duration = cDur,
                    syncedLyrics = cLyrics,
                    score = compositeScore
                )
            }
        }

        return candidateMap.values.sortedByDescending { it.score }
    }

    fun stringSimilarity(s1: String, s2: String): Float {
        val a = s1.lowercase().trim()
        val b = s2.lowercase().trim()
        if (a.isEmpty() && b.isEmpty()) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f
        if (a == b) return 1.0f

        val maxLen = max(a.length, b.length)
        val distance = levenshteinDistance(a, b)
        return (1.0f - distance.toFloat() / maxLen.toFloat()).coerceIn(0.0f, 1.0f)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(dp[j], dp[j - 1], prev)
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun enc(str: String): String = URLEncoder.encode(str, "UTF-8")

    private fun httpGet(urlStr: String): JSONObject? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                JSONObject(text)
            } else null
        } catch (e: Exception) { null }
    }

    private fun httpGetArray(urlStr: String): JSONArray? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                JSONArray(text)
            } else null
        } catch (e: Exception) { null }
    }
}

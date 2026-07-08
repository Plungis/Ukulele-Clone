package dev.arbjerg.ukulele.audio

import com.fasterxml.jackson.databind.ObjectMapper
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Component
class LyricsService {
    private val log = LoggerFactory.getLogger(LyricsService::class.java)
    private val objectMapper = ObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun find(track: AudioTrack): LyricsResult? {
        val title = track.info.title.trim().takeIf { it.isNotBlank() } ?: return null
        val artist = track.info.author.trim().takeIf { it.isNotBlank() && it != "unknown" }

        val query = buildString {
            append("track_name=${title.urlEncode()}")
            if (artist != null) append("&artist_name=${artist.urlEncode()}")
        }

        val request = HttpRequest.newBuilder(URI.create("$LRCLIB_API?$query"))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return null

            val result = objectMapper.readTree(response.body()).firstOrNull() ?: return null
            val trackName = result.get("trackName")?.asText()?.takeIf { it.isNotBlank() } ?: title
            val artistName = result.get("artistName")?.asText()?.takeIf { it.isNotBlank() } ?: artist.orEmpty()
            LyricsResult(trackName, artistName, searchUrl(trackName, artistName))
        } catch (e: Exception) {
            log.warn("Lyrics lookup failed", e)
            null
        }
    }

    private fun searchUrl(trackName: String, artistName: String): String {
        return "https://lrclib.net/search/${"$artistName $trackName".trim().urlEncode()}"
    }

    private fun String.urlEncode() = URLEncoder.encode(this, StandardCharsets.UTF_8)

    data class LyricsResult(
        val trackName: String,
        val artistName: String,
        val url: String
    )

    private companion object {
        const val LRCLIB_API = "https://lrclib.net/api/search"
        const val USER_AGENT = "Ukulele Discord Bot"
    }
}

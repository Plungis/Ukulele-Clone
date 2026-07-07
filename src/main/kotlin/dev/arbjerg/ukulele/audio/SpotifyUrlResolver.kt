package dev.arbjerg.ukulele.audio

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Component
class SpotifyUrlResolver {
    private val log = LoggerFactory.getLogger(SpotifyUrlResolver::class.java)
    private val objectMapper = ObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun isSpotifyUrl(identifier: String): Boolean {
        return SPOTIFY_URL.containsMatchIn(identifier)
    }

    fun resolveTrackSearch(identifier: String): String? {
        val spotifyUrl = SPOTIFY_URL.find(identifier)?.value ?: return null
        if (!spotifyUrl.contains("/track/")) return null

        val encoded = URLEncoder.encode(spotifyUrl, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("https://open.spotify.com/oembed?url=$encoded"))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("Spotify oEmbed lookup failed with status {}", response.statusCode())
                return null
            }

            val title = objectMapper.readTree(response.body()).get("title")?.asText()?.trim()
            title?.takeIf { it.isNotBlank() }?.let { "ytsearch:$it" }
        } catch (e: Exception) {
            log.warn("Spotify oEmbed fallback failed", e)
            null
        }
    }

    private companion object {
        val SPOTIFY_URL = Regex("https?://(?:www\\.)?open\\.spotify\\.com/[^\\s<>]+")
        const val USER_AGENT = "Mozilla/5.0 Ukulele"
    }
}

package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.audio.TtsTrackMeta
import dev.arbjerg.ukulele.audio.preview
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Component
class TtsCommand(
    private val players: PlayerRegistry,
    private val apm: AudioPlayerManager
) : Command("tts", "speak") {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override suspend fun CommandContext.invoke() {
        if (!ensureVoiceChannel()) return

        val text = argumentText.trim()
        if (text.isBlank()) return replyHelp()
        if (text.length > MAX_TTS_LENGTH) {
            return reply("Keep TTS under **$MAX_TTS_LENGTH** characters. Tiny speech, big boundaries.")
        }

        val voice = slashOption("voice")
            ?.takeIf { it in SUPPORTED_VOICES }
            ?: DEFAULT_VOICE
        val audioFile = try {
            downloadSpeech(text, voice)
        } catch (t: Throwable) {
            log.warn("TTS download failed", t)
            return reply("TTS generation failed before I could queue it: `${t.message}`")
        }
        val guildPlayer = players.get(guild, guildProperties)
        guildPlayer.lastChannel = channel

        apm.loadItem(audioFile.toAbsolutePath().toString(), Loader(this, guildPlayer, text, voice, audioFile))
    }

    private inner class Loader(
        private val ctx: CommandContext,
        private val player: dev.arbjerg.ukulele.audio.Player,
        private val text: String,
        private val voice: String,
        private val audioFile: Path
    ) : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            track.userData = TtsTrackMeta(text, ctx.invoker.effectiveName, audioFile.toAbsolutePath().toString())
            val started = player.add(track)
            val detail = "Queued TTS (${voice}): `${text.preview(90)}`."
            ctx.reply(if (started) "Speaking now: `${text.preview(90)}`." else detail)
            player.bumpPersistentControls()
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            playlist.tracks.firstOrNull()?.let { trackLoaded(it) } ?: noMatches()
        }

        override fun noMatches() {
            runCatching { Files.deleteIfExists(audioFile) }
            ctx.reply("I couldn't generate that TTS clip.")
        }

        override fun loadFailed(exception: FriendlyException) {
            runCatching { Files.deleteIfExists(audioFile) }
            ctx.reply("TTS generation failed: `${exception.message}`")
        }
    }

    override fun HelpContext.provideHelp() {
        addUsage("<text>")
        addDescription("Speaks text in your voice channel and adds it to the player queue.")
    }

    private fun speechUrl(text: String, voice: String): String {
        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val encodedVoice = URLEncoder.encode(voice, StandardCharsets.UTF_8)
        return "https://api.streamelements.com/kappa/v2/speech?voice=$encodedVoice&text=$encodedText"
    }

    private fun downloadSpeech(text: String, voice: String): Path {
        val dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "ukulele-tts"))
        val file = Files.createTempFile(dir, "tts-", ".mp3")
        val request = HttpRequest.newBuilder(URI.create(speechUrl(text, voice)))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Ukulele Discord Bot")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(file))
        if (response.statusCode() !in 200..299) {
            Files.deleteIfExists(file)
            error("TTS provider returned HTTP ${response.statusCode()}")
        }
        return file
    }

    private companion object {
        const val MAX_TTS_LENGTH = 240
        const val DEFAULT_VOICE = "Brian"
        val SUPPORTED_VOICES = setOf("Brian", "Amy", "Emma", "Joanna", "Matthew")
    }
}

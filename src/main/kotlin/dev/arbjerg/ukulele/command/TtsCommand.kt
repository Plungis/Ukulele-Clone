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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class TtsCommand(
    private val players: PlayerRegistry,
    private val apm: AudioPlayerManager
) : Command("tts", "speak") {
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
        val url = speechUrl(text, voice)
        val guildPlayer = players.get(guild, guildProperties)
        guildPlayer.lastChannel = channel
        guildPlayer.repostPersistentControls()

        apm.loadItem(url, Loader(this, guildPlayer, text, voice))
    }

    private inner class Loader(
        private val ctx: CommandContext,
        private val player: dev.arbjerg.ukulele.audio.Player,
        private val text: String,
        private val voice: String
    ) : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            track.userData = TtsTrackMeta(text, ctx.invoker.effectiveName)
            val started = player.add(track)
            val detail = "Queued TTS (${voice}): `${text.preview(90)}`."
            ctx.reply(if (started) "Speaking now: `${text.preview(90)}`." else detail)
            player.bumpPersistentControls()
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            playlist.tracks.firstOrNull()?.let { trackLoaded(it) } ?: noMatches()
        }

        override fun noMatches() {
            ctx.reply("I couldn't generate that TTS clip.")
        }

        override fun loadFailed(exception: FriendlyException) {
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

    private companion object {
        const val MAX_TTS_LENGTH = 240
        const val DEFAULT_VOICE = "Brian"
        val SUPPORTED_VOICES = setOf("Brian", "Amy", "Emma", "Joanna", "Matthew")
    }
}

package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.audio.SpotifyUrlResolver
import dev.arbjerg.ukulele.config.BotProps
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component

@Component
class PlayCommand(
        val players: PlayerRegistry,
        val apm: AudioPlayerManager,
        val botProps: BotProps,
        val spotifyUrlResolver: SpotifyUrlResolver
) : Command("play", "p") {
    override suspend fun CommandContext.invoke() {
        if (!ensureVoiceChannel()) return

        var identifier = argumentText
        if (!checkValidUrl(identifier)) {
            identifier = "ytsearch:$identifier"
        }

        val guildPlayer = players.get(guild, guildProperties)
        guildPlayer.lastChannel = channel
        apm.loadItem(identifier, Loader(this, player, identifier))
    }

    fun checkValidUrl(url: String): Boolean {
        return url.startsWith("http://")
                || url.startsWith("https://")
    }

    inner class Loader(
            private val ctx: CommandContext,
            private val player: Player,
            private val identifier: String,
            private val allowSpotifyFallback: Boolean = true
    ) : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            if (track.isOverDurationLimit) {
                ctx.reply("Refusing to play `${track.info.title}` because it is over ${botProps.trackDurationLimit} minutes long")
                return
            }
            val started = player.add(track)
            if (started) {
                ctx.reply("Started playing `${track.info.title}`")
            } else {
                ctx.reply("Added `${track.info.title}`")
            }
            player.bumpPersistentControls()
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            val accepted = playlist.tracks.filter { !it.isOverDurationLimit }
            val filteredCount = playlist.tracks.size - accepted.size
            if (accepted.isEmpty()) {
                ctx.reply("Refusing to play $filteredCount tracks because because they are all over ${botProps.trackDurationLimit} minutes long")
                return
            }

            if (identifier.startsWith("ytsearch") || identifier.startsWith("ytmsearch") || identifier.startsWith("scsearch:")) {
                this.trackLoaded(accepted.component1());
                return
            }

            player.add(*accepted.toTypedArray())
            ctx.reply(buildString {
                append("Added `${accepted.size}` tracks from `${playlist.name}`.")
                if (filteredCount != 0) append(" `$filteredCount` tracks have been ignored because they are over ${botProps.trackDurationLimit} minutes long")
            })
            player.bumpPersistentControls()
        }

        override fun noMatches() {
            if (trySpotifyFallback()) return
            ctx.reply("Nothing found for \"$identifier\"")
        }

        override fun loadFailed(exception: FriendlyException) {
            if (trySpotifyFallback()) return
            ctx.handleException(exception)
        }

        private fun trySpotifyFallback(): Boolean {
            if (!allowSpotifyFallback || !spotifyUrlResolver.isSpotifyUrl(identifier)) return false

            val fallbackIdentifier = spotifyUrlResolver.resolveTrackSearch(identifier)
            if (fallbackIdentifier == null) {
                ctx.reply(
                    "I couldn't resolve that Spotify link. Track links can usually be mirrored to YouTube, " +
                            "but albums and playlists need Spotify API credentials in `ukulele.yml`."
                )
                return true
            }

            ctx.reply("Spotify lookup had trouble, so I am matching it on YouTube instead.")
            apm.loadItem(fallbackIdentifier, Loader(ctx, player, fallbackIdentifier, allowSpotifyFallback = false))
            return true
        }

        private val AudioTrack.isOverDurationLimit: Boolean
            get() = botProps.trackDurationLimit > 0 && botProps.trackDurationLimit <= (duration / 60000)
    }

    override fun HelpContext.provideHelp() {
        addUsage("<url or search>")
        addDescription("Add a YouTube URL, Spotify URL, or YouTube search result to the queue")
    }
}

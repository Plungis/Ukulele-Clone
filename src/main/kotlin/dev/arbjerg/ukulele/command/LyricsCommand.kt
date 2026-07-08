package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.audio.LyricsService
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import net.dv8tion.jda.api.EmbedBuilder
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class LyricsCommand(
    private val lyricsService: LyricsService
) : Command("lyrics", "lyric") {
    override suspend fun CommandContext.invoke() {
        val track = player.tracks.firstOrNull() ?: return reply("Not playing anything.")
        val result = lyricsService.find(track) ?: return reply("I couldn't find lyrics for `${track.info.title}`.")

        replyEmbed(
            EmbedBuilder()
                .setTitle("Lyrics Found", result.url)
                .setDescription("Found a lyrics source for **${result.trackName}**${artistSuffix(result.artistName)}.")
                .setColor(Color(88, 101, 242))
                .build()
        )
    }

    private fun artistSuffix(artistName: String): String {
        return if (artistName.isBlank()) "" else " by **$artistName**"
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Finds a lyrics source for the current track.")
    }
}

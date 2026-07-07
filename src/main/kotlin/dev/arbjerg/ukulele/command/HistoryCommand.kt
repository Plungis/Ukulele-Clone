package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import dev.arbjerg.ukulele.utils.TextUtils
import net.dv8tion.jda.api.EmbedBuilder
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class HistoryCommand : Command("history", "played") {
    override suspend fun CommandContext.invoke() {
        val history = player.history.takeLast(10).asReversed()
        if (history.isEmpty()) return reply("Nothing has played this session yet.")

        replyEmbed(
            EmbedBuilder()
                .setTitle("Recently Played")
                .setColor(Color(88, 101, 242))
                .setDescription(history.joinToString("\n") {
                    val author = if (it.author.isBlank()) "" else " - ${it.author}"
                    "**${it.title}**$author `[${TextUtils.humanReadableTime(it.playedMillis)}]`"
                })
                .setFooter("Session play time: ${TextUtils.humanReadableTime(player.sessionPlayedDuration)}")
                .build()
        )
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Shows recently played tracks and session play time.")
    }
}

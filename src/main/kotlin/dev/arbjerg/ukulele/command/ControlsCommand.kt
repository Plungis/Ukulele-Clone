package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class ControlsCommand : Command("controls", "panel") {
    override suspend fun CommandContext.invoke() {
        replyMsg(buildMessage(player))
    }

    fun buildMessage(player: Player): MessageCreateData {
        val current = player.tracks.firstOrNull()
        val embed = EmbedBuilder()
            .setTitle("Ukulele Player")
            .setColor(Color(88, 101, 242))
            .setDescription(current?.let {
                "Now playing: **${it.info.title}**\n${player.buildSessionSummary()}"
            } ?: "Nothing is playing.\n${player.buildSessionSummary()}")
            .build()

        return MessageCreateBuilder()
            .addEmbeds(embed)
            .addComponents(
                listOf(
                    ActionRow.of(NowPlayingCommand.playbackButtons(player.isPaused)),
                    ActionRow.of(controlMenu())
                )
            )
            .build()
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Shows a player control panel with buttons and a menu.")
    }

    private fun controlMenu() = StringSelectMenu.create("ukulele:menu")
        .setPlaceholder("More player controls")
        .addOption("Now playing", "nowplaying", "Refresh the current track details")
        .addOption("Queue", "queue", "Show the current queue")
        .addOption("History", "history", "Show what has played this session")
        .addOption("Shuffle", "shuffle", "Shuffle upcoming tracks")
        .addOption("Repeat", "repeat", "Toggle queue repeat")
        .build()
}

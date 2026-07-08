package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.audio.displayTitle
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import dev.arbjerg.ukulele.utils.TextUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class QueueCommand(
        private val players: PlayerRegistry
) : Command("queue", "q", "list", "l") {

    private val pageSize = 10

    override suspend fun CommandContext.invoke() {
        replyMsg(buildMessage(player, argumentText.toIntOrNull() ?: 1))
    }

    fun buildMessage(player: Player, pageIndex: Int): MessageCreateData {
        val tracks = player.tracks
        if (tracks.isEmpty()) {
            return MessageCreateBuilder()
                .addContent("The queue is empty.")
                .build()
        }

        val pageCount = getPageCount(tracks)
        val page = pageIndex.coerceIn(1..pageCount)

        return MessageCreateBuilder()
            .addEmbeds(buildEmbed(player, page))
            .addComponents(listOf(ActionRow.of(queueButtons(page, pageCount))))
            .build()
    }

    fun buildEmbed(player: Player, pageIndex: Int): MessageEmbed {
        val totalDuration = player.remainingDuration
        val tracks = player.tracks
        val pageCount = getPageCount(tracks)
        val page = pageIndex.coerceIn(1..pageCount)

        val description = buildString {
            append(paginateQueue(tracks, page))
            appendLine()
            append("There are **${tracks.size}** tracks with a remaining length of ")

            if (tracks.any{ it.info.isStream }) {
                append("**${TextUtils.humanReadableTime(totalDuration)}** in the queue excluding streams.")
            } else {
                append("**${TextUtils.humanReadableTime(totalDuration)}** in the queue.")
            }
        }

        return EmbedBuilder()
            .setTitle("Queue")
            .setDescription(description)
            .setFooter("Page $page of $pageCount")
            .setColor(Color(88, 101, 242))
            .build()
    }

    private fun paginateQueue(tracks: List<AudioTrack>, index: Int) = buildString {
        val pageCount = getPageCount(tracks)
        val pageIndex = index.coerceIn(1..pageCount)

        val offset = pageSize * (pageIndex - 1)
        val pageEnd = (offset + pageSize).coerceAtMost(tracks.size)

        tracks.subList(offset, pageEnd).forEachIndexed { i, t ->
            appendLine("`[${offset + i + 1}]` **${t.displayTitle}** `[${if (t.info.isStream) "Live" else TextUtils.humanReadableTime(t.duration)}]`")
        }
    }

    private fun getPageCount(tracks: List<AudioTrack>) = (tracks.size + pageSize - 1) / pageSize

    private fun queueButtons(page: Int, pageCount: Int): List<Button> {
        return listOf(
            Button.secondary("ukulele:queue:${page - 1}", "Previous").withDisabled(page <= 1),
            Button.secondary("ukulele:queue:${page + 1}", "Next").withDisabled(page >= pageCount)
        )
    }

    override fun HelpContext.provideHelp() {
        addUsage("[page]")
        addDescription("Displays the queue, by default for page 1")
    }
}

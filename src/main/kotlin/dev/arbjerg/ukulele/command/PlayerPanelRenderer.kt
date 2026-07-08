package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioTrack
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.utils.TextUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class PlayerPanelRenderer {
    fun build(player: Player): MessageCreateData {
        val track = player.tracks.firstOrNull()
        return MessageCreateBuilder()
            .addEmbeds(buildEmbed(player, track))
            .addComponents(
                listOf(
                    ActionRow.of(playbackButtons(player, track != null)),
                    ActionRow.of(controlMenu(track != null))
                )
            )
            .build()
    }

    fun buildEmbed(player: Player, track: AudioTrack? = player.tracks.firstOrNull()): MessageEmbed {
        val isIdle = track == null && !player.isConnected
        val embed = EmbedBuilder()
            .setTitle(
                when {
                    isIdle -> "Idle"
                    track == null -> "Ukulele Player"
                    else -> "Now Playing"
                }
            )
            .setColor(track?.panelColor() ?: DEFAULT_BLUE)
            .setFooter("Ukulele player panel")

        if (track == null) {
            embed.setDescription(
                if (isIdle) {
                    "No music is playing and I am not connected to voice."
                } else {
                    "Nothing is playing right now."
                }
            )
            embed.addField("Session", player.sessionStats(), true)
            return embed.build()
        }

        val author = track.info.author.takeIf { it.isNotBlank() && it != "unknown" }
        embed.setDescription(buildString {
            append("**${track.info.title.abbreviate(240)}**")
            if (author != null) append("\nby ${author.abbreviate(120)}")
            if (track.info.uri.isNotBlank()) append("\n${track.info.uri}")
        })
        embed.addField("Progress", progressLine(track), false)
        embed.addField("Queue", queueStats(player), true)
        embed.addField("Session", player.sessionStats(), true)

        val next = player.tracks.drop(1).firstOrNull()
        if (next != null) {
            embed.addField("Up next", next.info.title.abbreviate(120), false)
        }

        return embed.build()
    }

    private fun playbackButtons(player: Player, hasTrack: Boolean): List<Button> {
        val pauseOrResume = if (player.isPaused) {
            Button.success("ukulele:resume", "Resume")
        } else {
            Button.secondary("ukulele:pause", "Pause")
        }

        return listOf(
            pauseOrResume.withDisabled(!hasTrack),
            Button.primary("ukulele:skip", "Skip").withDisabled(!hasTrack),
            Button.secondary("ukulele:queue:1", "Queue").withDisabled(player.tracks.isEmpty()),
            Button.danger("ukulele:stop", "Stop").withDisabled(player.tracks.isEmpty())
        )
    }

    private fun controlMenu(enabled: Boolean) = StringSelectMenu.create("ukulele:menu")
        .setPlaceholder("More player controls")
        .addOption("Refresh panel", "panel", "Return to the live player panel")
        .addOption("Now playing", "nowplaying", "Show the current track details")
        .addOption("Queue", "queue", "Show the current queue")
        .addOption("History", "history", "Show what has played this session")
        .addOption("Shuffle", "shuffle", "Shuffle upcoming tracks")
        .addOption("Repeat", "repeat", "Toggle queue repeat")
        .build()
        .withDisabled(!enabled)

    private fun progressLine(track: AudioTrack): String {
        if (track.info.isStream) return "`LIVE`"

        val position = track.position.coerceAtLeast(0)
        val length = track.info.length.coerceAtLeast(1)
        val filled = ((position.toDouble() / length.toDouble()) * PROGRESS_WIDTH).toInt().coerceIn(0, PROGRESS_WIDTH)
        val bar = "=".repeat(filled) + "-".repeat(PROGRESS_WIDTH - filled)

        return "`[$bar] ${TextUtils.humanReadableTime(position)} / ${TextUtils.humanReadableTime(length)}`"
    }

    private fun queueStats(player: Player): String {
        val upcoming = (player.tracks.size - 1).coerceAtLeast(0)
        return buildString {
            append("Upcoming: **$upcoming**")
            if (player.remainingDuration > 0) {
                append("\nRemaining: **${TextUtils.humanReadableTime(player.remainingDuration)}**")
            }
            append("\nRepeat: **${if (player.isRepeating) "On" else "Off"}**")
        }
    }

    private fun Player.sessionStats(): String {
        return "Played: **${history.size}**\nTime: **${TextUtils.humanReadableTime(sessionPlayedDuration)}**"
    }

    private fun AudioTrack.panelColor(): Int {
        return when (this) {
            is YoutubeAudioTrack -> YOUTUBE_RED
            is SoundCloudAudioTrack -> SOUNDCLOUD_ORANGE
            is TwitchStreamAudioTrack -> TWITCH_PURPLE
            else -> DEFAULT_BLUE
        }
    }

    private fun String.abbreviate(maxLength: Int): String {
        if (length <= maxLength) return this
        return take(maxLength - 3) + "..."
    }

    private companion object {
        const val PROGRESS_WIDTH = 18
        val DEFAULT_BLUE = Color(88, 101, 242).rgb
        val YOUTUBE_RED = Color(205, 32, 31).rgb
        val SOUNDCLOUD_ORANGE = Color(255, 85, 0).rgb
        val TWITCH_PURPLE = Color(100, 65, 164).rgb
    }
}

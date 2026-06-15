package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioTrack
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
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
class NowPlayingCommand : Command ("nowplaying", "np") {

    override suspend fun CommandContext.invoke() {
        if (player.tracks.isEmpty())
            return reply("Not playing anything.")

        replyMsg(buildMessage(player.tracks[0], player.isPaused))
    }

    fun buildMessage(track: AudioTrack, isPaused: Boolean): MessageCreateData {
        return MessageCreateBuilder()
            .addEmbeds(buildEmbed(track))
            .addComponents(listOf(ActionRow.of(playbackButtons(isPaused))))
            .build()
    }

    fun buildEmbed(track: AudioTrack): MessageEmbed {
        return when(track){
            is YoutubeAudioTrack -> GetEmbed(track).youtube()
            is SoundCloudAudioTrack -> GetEmbed(track).soundcloud()
            is TwitchStreamAudioTrack -> GetEmbed(track).twitch()
            else -> GetEmbed(track).default()
        }
    }

    private class GetEmbed(val track: AudioTrack) {
        val timeField = if (track.info.isStream) "[Live]" else "[${TextUtils.humanReadableTime(track.position)} / ${TextUtils.humanReadableTime(track.info.length)}]"

        //Set up common parts of the embed
        val message = EmbedBuilder()
                .setTitle(track.info.title, track.info.uri)
                .setFooter("Source: ${track.sourceManager.sourceName}")

        //Prepare embeds for overrides.
        fun youtube(): MessageEmbed {
            message.setColor(YOUTUBE_RED)
            message.addField("Time", timeField, true)
            return message.build()
        }

        fun soundcloud(): MessageEmbed {
            message.setColor(SOUNDCLOUD_ORANGE)
            message.addField("Time", timeField, true)
            return message.build()
        }

        fun twitch(): MessageEmbed {
            message.setColor(TWITCH_PURPLE)
            return message.build()
        }

        fun default(): MessageEmbed {
            message.setTitle(track.info.title)  // Show just the title of the radio station. Weird uri jank.
            message.setColor(DEFAULT_GREY)
            message.addField("Time", timeField, true)
            return message.build()
        }
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Displays information about the currently playing song.")
    }

    private companion object {
        fun playbackButtons(isPaused: Boolean): List<Button> {
            val pauseOrResume = if (isPaused) {
                Button.success("ukulele:resume", "Resume")
            } else {
                Button.secondary("ukulele:pause", "Pause")
            }

            return listOf(
                pauseOrResume,
                Button.primary("ukulele:skip", "Skip")
            )
        }

        val YOUTUBE_RED = Color(205, 32, 31).rgb
        val SOUNDCLOUD_ORANGE = Color(255, 85, 0).rgb
        val TWITCH_PURPLE = Color(100, 65, 164).rgb
        val DEFAULT_GREY = Color(100, 100, 100).rgb
    }
}

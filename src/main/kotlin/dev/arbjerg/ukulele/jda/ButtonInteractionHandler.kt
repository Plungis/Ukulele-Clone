package dev.arbjerg.ukulele.jda

import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.command.NowPlayingCommand
import dev.arbjerg.ukulele.command.QueueCommand
import dev.arbjerg.ukulele.data.GuildProperties
import dev.arbjerg.ukulele.data.GuildPropertiesService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.springframework.stereotype.Service

@Service
class ButtonInteractionHandler(
    private val guildPropertiesService: GuildPropertiesService,
    private val players: PlayerRegistry,
    private val nowPlayingCommand: NowPlayingCommand,
    private val queueCommand: QueueCommand
) {

    fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonId = event.componentId
        if (!buttonId.startsWith(BUTTON_PREFIX)) return
        if (event.guild == null || event.channelType != ChannelType.TEXT) {
            event.reply("Music controls can only be used in a server text channel.").setEphemeral(true).queue()
            return
        }

        GlobalScope.launch {
            val guild = event.guild!!
            val guildProperties = guildPropertiesService.getAwait(guild.idLong)
            val musicChannelId = guildProperties.musicChannelId
            if (musicChannelId != null && event.channel.idLong != musicChannelId) {
                event.reply("Music controls are restricted to <#$musicChannelId>.").setEphemeral(true).queue()
                return@launch
            }

            val player = players.get(guild, guildProperties)
            when {
                buttonId == "$BUTTON_PREFIX:pause" -> pause(event, player)
                buttonId == "$BUTTON_PREFIX:resume" -> resume(event, player)
                buttonId == "$BUTTON_PREFIX:skip" -> skip(event, player)
                buttonId.startsWith("$BUTTON_PREFIX:queue:") -> paginateQueue(event, player, buttonId)
            }
        }
    }

    private fun pause(event: ButtonInteractionEvent, player: Player) {
        if (player.tracks.isEmpty()) {
            event.reply("Not playing anything.").setEphemeral(true).queue()
            return
        }

        player.pause()
        editNowPlaying(event, player)
    }

    private fun resume(event: ButtonInteractionEvent, player: Player) {
        if (player.tracks.isEmpty()) {
            event.reply("Not playing anything.").setEphemeral(true).queue()
            return
        }

        player.resume()
        editNowPlaying(event, player)
    }

    private fun skip(event: ButtonInteractionEvent, player: Player) {
        val skipped = player.skip(0..0)
        if (skipped.isEmpty()) {
            event.reply("Nothing to skip.").setEphemeral(true).queue()
            return
        }

        editNowPlaying(event, player)
    }

    private fun editNowPlaying(event: ButtonInteractionEvent, player: Player) {
        val currentTrack = player.tracks.firstOrNull()
        if (currentTrack == null) {
            event.editMessage("The queue is empty.").setComponents(emptyList()).queue()
            return
        }

        event.editMessage(toEditData(nowPlayingCommand.buildMessage(currentTrack, player.isPaused))).queue()
    }

    private fun paginateQueue(event: ButtonInteractionEvent, player: Player, buttonId: String) {
        val page = buttonId.substringAfterLast(":").toIntOrNull() ?: 1
        event.editMessage(toEditData(queueCommand.buildMessage(player, page))).queue()
    }

    private fun toEditData(message: MessageCreateData): MessageEditData {
        return MessageEditBuilder.fromCreateData(message).build()
    }

    private companion object {
        const val BUTTON_PREFIX = "ukulele"
    }
}

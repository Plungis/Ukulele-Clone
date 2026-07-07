package dev.arbjerg.ukulele.jda

import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.command.ControlsCommand
import dev.arbjerg.ukulele.command.NowPlayingCommand
import dev.arbjerg.ukulele.command.QueueCommand
import dev.arbjerg.ukulele.data.GuildProperties
import dev.arbjerg.ukulele.data.GuildPropertiesService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.springframework.stereotype.Service
import java.awt.Color

@Service
class ButtonInteractionHandler(
    private val guildPropertiesService: GuildPropertiesService,
    private val players: PlayerRegistry,
    private val nowPlayingCommand: NowPlayingCommand,
    private val queueCommand: QueueCommand,
    private val controlsCommand: ControlsCommand
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
                buttonId == "$BUTTON_PREFIX:stop" -> stop(event, player)
                buttonId == "$BUTTON_PREFIX:refresh" -> editControls(event, player)
                buttonId.startsWith("$BUTTON_PREFIX:queue:") -> paginateQueue(event, player, buttonId)
            }
        }
    }

    fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        if (event.componentId != "$BUTTON_PREFIX:menu") return
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
            when (event.values.firstOrNull()) {
                "panel" -> editSelect(event, controlsCommand.buildMessage(player))
                "nowplaying" -> editSelect(event, nowPlayingMessage(player))
                "queue" -> editSelect(event, queueCommand.buildMessage(player, 1))
                "history" -> editSelect(event, historyMessage(player))
                "shuffle" -> {
                    player.shuffle()
                    editSelect(event, controlsCommand.buildMessage(player))
                }
                "repeat" -> {
                    player.isRepeating = !player.isRepeating
                    editSelect(event, controlsCommand.buildMessage(player))
                }
            }
        }
    }

    private fun pause(event: ButtonInteractionEvent, player: Player) {
        if (player.tracks.isEmpty()) {
            event.reply("Not playing anything.").setEphemeral(true).queue()
            return
        }

        player.pause()
        editControls(event, player)
    }

    private fun resume(event: ButtonInteractionEvent, player: Player) {
        if (player.tracks.isEmpty()) {
            event.reply("Not playing anything.").setEphemeral(true).queue()
            return
        }

        player.resume()
        editControls(event, player)
    }

    private fun skip(event: ButtonInteractionEvent, player: Player) {
        val skipped = player.skip(0..0)
        if (skipped.isEmpty()) {
            event.reply("Nothing to skip.").setEphemeral(true).queue()
            return
        }

        editControls(event, player)
    }

    private fun stop(event: ButtonInteractionEvent, player: Player) {
        if (player.tracks.isEmpty()) {
            event.reply("Not playing anything.").setEphemeral(true).queue()
            return
        }

        player.stop()
        editControls(event, player)
    }

    private fun editControls(event: ButtonInteractionEvent, player: Player) {
        event.editMessage(toEditData(controlsCommand.buildMessage(player))).queue()
    }

    private fun paginateQueue(event: ButtonInteractionEvent, player: Player, buttonId: String) {
        val page = buttonId.substringAfterLast(":").toIntOrNull() ?: 1
        event.editMessage(toEditData(queueCommand.buildMessage(player, page))).queue()
    }

    private fun nowPlayingMessage(player: Player): MessageCreateData {
        return if (player.tracks.isEmpty()) {
            MessageCreateData.fromContent("The queue is empty.")
        } else {
            nowPlayingCommand.buildMessage(player)
        }
    }

    private fun historyMessage(player: Player): MessageCreateData {
        val history = player.history.takeLast(10).asReversed()
        if (history.isEmpty()) return MessageCreateData.fromContent("Nothing has played this session yet.")

        val embed = EmbedBuilder()
            .setTitle("Recently Played")
            .setColor(Color(88, 101, 242))
            .setDescription(player.buildSessionSummary())
            .build()

        return MessageCreateData.fromEmbeds(embed)
    }

    private fun toEditData(message: MessageCreateData): MessageEditData {
        return MessageEditBuilder.fromCreateData(message).build()
    }

    private fun editSelect(event: StringSelectInteractionEvent, message: MessageCreateData) {
        event.editMessage(toEditData(message)).queue()
    }

    private companion object {
        const val BUTTON_PREFIX = "ukulele"
    }
}

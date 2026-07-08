package dev.arbjerg.ukulele.jda

import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.data.GuildPropertiesService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.StatusChangeEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val commandManager: CommandManager,
    private val buttonInteractionHandler: ButtonInteractionHandler,
    private val players: PlayerRegistry,
    private val guildPropertiesService: GuildPropertiesService
) : ListenerAdapter() {

    private val log: Logger = LoggerFactory.getLogger(EventHandler::class.java)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.isWebhookMessage || event.author.isBot) return
        if (event.channelType != ChannelType.TEXT) return

        commandManager.onMessage(event.guild, event.channel.asTextChannel(), event.member!!, event.message)
        GlobalScope.launch {
            val guildProperties = guildPropertiesService.getAwait(event.guild.idLong)
            if (guildProperties.panelChannelId == event.channel.idLong) {
                players.get(event.guild, guildProperties).bumpPersistentControls(event.channel.asTextChannel())
            } else {
                players.find(event.guild.idLong)?.bumpPersistentControls(event.channel.asTextChannel())
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        buttonInteractionHandler.onButtonInteraction(event)
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        buttonInteractionHandler.onStringSelectInteraction(event)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        commandManager.onSlashCommand(event)
    }

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        players.find(event.guild.idLong)?.onVoiceStateChanged()
    }

    override fun onReady(event: ReadyEvent) {
        if (event.jda.shardInfo.shardId == 0) {
            commandManager.registerSlashCommands(event.jda)
        }
    }

    override fun onStatusChange(event: StatusChangeEvent) {
        log.info("{}: {} -> {}", event.entity.shardInfo, event.oldStatus, event.newStatus)
    }

}

package dev.arbjerg.ukulele.jda

import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.StatusChangeEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EventHandler(
    private val commandManager: CommandManager,
    private val buttonInteractionHandler: ButtonInteractionHandler
) : ListenerAdapter() {

    private val log: Logger = LoggerFactory.getLogger(EventHandler::class.java)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.isWebhookMessage || event.author.isBot) return
        if (event.channelType != ChannelType.TEXT) return

        commandManager.onMessage(event.guild, event.channel.asTextChannel(), event.member!!, event.message)
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        buttonInteractionHandler.onButtonInteraction(event)
    }

    override fun onStatusChange(event: StatusChangeEvent) {
        log.info("{}: {} -> {}", event.entity.shardInfo, event.oldStatus, event.newStatus)
    }

}

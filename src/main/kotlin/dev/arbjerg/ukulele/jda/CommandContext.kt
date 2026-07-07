package dev.arbjerg.ukulele.jda

import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.audio.PlayerRegistry
import dev.arbjerg.ukulele.config.BotProps
import dev.arbjerg.ukulele.data.GuildProperties
import dev.arbjerg.ukulele.features.HelpContext
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.springframework.stereotype.Component

class CommandContext(
    val beans: Beans,
    val guildProperties: GuildProperties,
    val guild: Guild,
    val channel: TextChannel,
    val invoker: Member,
    val message: Message?,
    val command: Command,
    val prefix: String,
    /** Prefix + command name */
        val trigger: String,
    private val slashEvent: SlashCommandInteractionEvent? = null,
    private val slashArgumentText: String? = null
) {
    @Component
    class Beans(
            val players: PlayerRegistry,
            val botProps: BotProps
    ) {
        lateinit var commandManager: CommandManager
    }

    val player: Player by lazy { beans.players.get(guild, guildProperties) }

    /** The command argument text after the trigger */
    val argumentText: String by lazy {
        slashArgumentText ?: message?.contentRaw?.drop(trigger.length)?.trim().orEmpty()
    }
    val selfMember: Member get() = guild.selfMember

    fun reply(msg: String) {
        slashEvent?.replyOrFollowUp(MessageCreateData.fromContent(msg)) ?: channel.sendMessage(msg).queue()
    }

    fun replyMsg(msg: MessageCreateData) {
        slashEvent?.replyOrFollowUp(msg) ?: channel.sendMessage(msg).queue()
    }

    fun replyEmbed(embed: MessageEmbed) {
        replyMsg(MessageCreateData.fromEmbeds(embed))
    }

    fun replyHelp(forCommand: Command = command) {
        val help = HelpContext(this, forCommand)
        forCommand.provideHelp0(help)
        replyMsg(help.buildMessage())
    }

    fun handleException(t: Throwable) {
        command.log.error("Handled exception occurred", t)
        reply("An exception occurred!\n`${t.message}`")
    }

    private fun SlashCommandInteractionEvent.replyOrFollowUp(message: MessageCreateData) {
        if (!isAcknowledged) {
            reply(message).queue()
            return
        }

        hook.editOriginal(MessageEditBuilder.fromCreateData(message).build()).queue()
    }
}

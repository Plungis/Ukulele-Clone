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
    val isSlashCommand: Boolean get() = slashEvent != null

    fun slashOption(name: String): String? = slashEvent?.getOption(name)?.asString

    fun reply(msg: String) {
        val panelPlayer = panelPlayerForChannel()
        panelPlayer?.addCommandLog(command.name, msg, channel)

        slashEvent?.replyOrFollowUp(MessageCreateData.fromContent(msg)) ?: run {
            if (panelPlayer == null) {
                channel.sendMessage(msg).queue {
                    bumpPersistentControls()
                }
            }
        }
    }

    fun replyMsg(msg: MessageCreateData) {
        val panelPlayer = panelPlayerForChannel()
        val summary = messageSummary(msg)
        if (summary.isNotBlank()) panelPlayer?.addCommandLog(command.name, summary, channel)

        slashEvent?.replyOrFollowUp(msg) ?: run {
            if (panelPlayer == null) {
                channel.sendMessage(msg).queue {
                    bumpPersistentControls()
                }
            }
        }
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
            reply(message).queue {
                bumpPersistentControls()
            }
            return
        }

        hook.editOriginal(MessageEditBuilder.fromCreateData(message).build()).queue {
            bumpPersistentControls()
        }
    }

    private fun bumpPersistentControls() {
        beans.players.find(guild.idLong)?.bumpPersistentControls(channel)
    }

    private fun panelPlayerForChannel(): Player? {
        val existing = beans.players.find(guild.idLong)
        if (existing?.hasPersistentControlsFor(channel) == true) return existing
        if (guildProperties.panelChannelId != channel.idLong) return null

        return player.apply { restoreConfiguredPanelChannel() }
            .takeIf { it.hasPersistentControlsFor(channel) }
    }

    private fun messageSummary(message: MessageCreateData): String {
        if (message.content.isNotBlank()) return message.content
        return message.embeds.firstOrNull()?.let { embed ->
            listOfNotNull(embed.title, embed.description).joinToString(" - ")
        }.orEmpty()
    }
}

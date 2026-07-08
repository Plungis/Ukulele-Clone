package dev.arbjerg.ukulele.jda

import dev.arbjerg.ukulele.config.BotProps
import dev.arbjerg.ukulele.data.GuildPropertiesService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.Permission
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CommandManager(
        private val contextBeans: CommandContext.Beans,
        private val guildProperties: GuildPropertiesService,
        private val botProps: BotProps,
        commands: Collection<Command>
) {

    private final val registry: Map<String, Command>
    private val log: Logger = LoggerFactory.getLogger(CommandManager::class.java)

    init {
        val map = mutableMapOf<String, Command>()
        commands.forEach { c ->
            map[c.name] = c
            c.aliases.forEach { map[it] = c }
        }
        registry = map
        log.info("Registered ${commands.size} commands with ${registry.size} names")
        @Suppress("LeakingThis")
        contextBeans.commandManager = this
    }

    operator fun get(commandName: String) = registry[commandName]

    fun getCommands() = registry.values.distinct()

    fun registerSlashCommands(jda: JDA) {
        jda.updateCommands().addCommands(slashCommands()).queue {
            log.info("Registered ${it.size} global slash commands")
        }
    }

    fun onSlashCommand(event: SlashCommandInteractionEvent) {
        if (event.guild == null || event.member == null || event.channelType != net.dv8tion.jda.api.entities.channel.ChannelType.TEXT) {
            event.reply("Music slash commands can only be used in a server text channel.").setEphemeral(true).queue()
            return
        }

        val command = registry[event.name] ?: return
        event.deferReply(event.name == "show" || event.name == "controls").queue()

        GlobalScope.launch {
            val guild = event.guild!!
            val channel = event.channel.asTextChannel()
            val guildProperties = guildProperties.getAwait(guild.idLong)

            if (!command.bypassMusicChannelRestriction) {
                val musicChannelId = guildProperties.musicChannelId
                if (musicChannelId != null && channel.idLong != musicChannelId) {
                    event.hook.editOriginal(WrongChannelMessages.pick("<#$musicChannelId>")).queue()
                    return@launch
                }
            }

            val ctx = CommandContext(
                contextBeans,
                guildProperties,
                guild,
                channel,
                event.member!!,
                null,
                command,
                botProps.prefix,
                "/${event.name}",
                event,
                slashArgumentText(event)
            )

            log.info("Slash invocation: /${event.name}")
            command.invoke0(ctx)
        }
    }

    fun onMessage(guild: Guild, channel: TextChannel, member: Member, message: Message) {
        GlobalScope.launch {
            val guildProperties = guildProperties.getAwait(guild.idLong)
            val prefix = guildProperties.prefix ?: botProps.prefix

            val name: String
            val trigger: String

            // match result: a mention of us at the beginning
            val mention = Regex("^(<@!?${guild.getSelfMember().getId()}>\\s*)").find(message.contentRaw)?.value
            if (mention != null) {
                val commandText = message.contentRaw.drop(mention.length)
                if (commandText.isEmpty()) {
                    channel.sendMessage("The prefix here is `${prefix}`, or just mention me followed by a command.").queue()
                    return@launch
                }

                name = commandText.trim().takeWhile { !it.isWhitespace() }
                trigger = mention + name
            } else if (message.contentRaw.startsWith(prefix)) {
                name = message.contentRaw.drop(prefix.length)
                        .takeWhile { !it.isWhitespace() }
                trigger = prefix + name
            } else {
                return@launch
            }

            val command = registry[name] ?: return@launch
            if (!command.bypassMusicChannelRestriction) {
                val musicChannelId = guildProperties.musicChannelId
                if (musicChannelId != null && channel.idLong != musicChannelId) {
                    val musicChannel = guild.getTextChannelById(musicChannelId)
                    val channelName = musicChannel?.asMention ?: "<#$musicChannelId>"
                    channel.sendMessage(WrongChannelMessages.pick(channelName)).queue()
                    return@launch
                }
            }

            val ctx = CommandContext(contextBeans, guildProperties, guild, channel, member, message, command, prefix, trigger)

            log.info("Invocation: ${message.contentRaw}")
            command.invoke0(ctx)
        }
    }

    private fun slashCommands(): List<CommandData> {
        return listOf(
            Commands.slash("play", "Play a YouTube or Spotify URL, or search YouTube.")
                .addOption(OptionType.STRING, "query", "URL or search terms", true),
            Commands.slash("pause", "Pause playback."),
            Commands.slash("resume", "Resume playback."),
            Commands.slash("skip", "Skip the current track or a range.")
                .addOption(OptionType.STRING, "tracks", "Optional track index or range, like 2 or 2 5"),
            Commands.slash("stop", "Stop playback, clear the queue, and leave voice."),
            Commands.slash("queue", "Show the queue.")
                .addOption(OptionType.INTEGER, "page", "Queue page"),
            Commands.slash("nowplaying", "Show the current track and playback controls."),
            Commands.slash("controls", "Show the premium player control panel."),
            Commands.slash("show", "Show the persistent premium player control panel."),
            Commands.slash("musicchannel", "Restrict music commands to one text channel.")
                .addOption(OptionType.CHANNEL, "channel", "Text channel to use for music commands")
                .addOption(OptionType.BOOLEAN, "reset", "Allow music commands in any text channel")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),
            Commands.slash("history", "Show recently played tracks and session play time."),
            Commands.slash("shuffle", "Shuffle upcoming tracks."),
            Commands.slash("repeat", "Toggle queue repeat."),
            Commands.slash("volume", "Show or set the playback volume.")
                .addOption(OptionType.INTEGER, "level", "Volume percentage from 0 to 150"),
            Commands.slash("seek", "Seek the current track.")
                .addOption(OptionType.STRING, "position", "Timestamp like 1:23 or 01:02:03", true)
        )
    }

    private fun slashArgumentText(event: SlashCommandInteractionEvent): String {
        return when (event.name) {
            "play" -> event.getOption("query")?.asString.orEmpty()
            "skip" -> event.getOption("tracks")?.asString.orEmpty()
            "queue" -> event.getOption("page")?.asLong?.toString().orEmpty()
            "volume" -> event.getOption("level")?.asLong?.toString().orEmpty()
            "seek" -> event.getOption("position")?.asString.orEmpty()
            "musicchannel" -> {
                if (event.getOption("reset")?.asBoolean == true) {
                    "reset"
                } else {
                    event.getOption("channel")?.asChannel?.asMention.orEmpty()
                }
            }
            else -> ""
        }
    }

}

package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.data.GuildPropertiesService
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import net.dv8tion.jda.api.Permission
import org.springframework.stereotype.Component

@Component
class MusicChannelCommand(
    private val guildPropertiesService: GuildPropertiesService
) : Command("musicchannel", "music-channel", "musicroom", bypassMusicChannelRestriction = true) {

    override suspend fun CommandContext.invoke() {
        if (!invoker.hasPermission(Permission.MANAGE_SERVER)) {
            reply("You need the Manage Server permission to change the music channel.")
            return
        }

        when {
            argumentText.equals("reset", ignoreCase = true) -> {
                guildPropertiesService.transformAwait(guild.idLong) { it.musicChannelId = null }
                reply("Music commands can now be used in any text channel.")
            }
            argumentText.isBlank() -> setMusicChannel(channel.idLong)
            else -> {
                val channelId = parseChannelId(argumentText)
                val textChannel = channelId?.let { guild.getTextChannelById(it) }
                if (textChannel == null) {
                    reply("I couldn't find that text channel.")
                    return
                }

                setMusicChannel(textChannel.idLong)
            }
        }
    }

    private suspend fun CommandContext.setMusicChannel(channelId: Long) {
        guildPropertiesService.transformAwait(guild.idLong) { it.musicChannelId = channelId }
        reply("Music commands are now restricted to <#$channelId>.")
    }

    private fun parseChannelId(text: String): Long? {
        val trimmed = text.trim()
        return Regex("^<#(\\d+)>$").matchEntire(trimmed)?.groupValues?.get(1)?.toLongOrNull()
            ?: trimmed.toLongOrNull()
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Restricts music commands to the current text channel.")
        addUsage("<#channel>")
        addDescription("Restricts music commands to the mentioned text channel.")
        addUsage("reset")
        addDescription("Allows music commands in every text channel again.")
    }
}

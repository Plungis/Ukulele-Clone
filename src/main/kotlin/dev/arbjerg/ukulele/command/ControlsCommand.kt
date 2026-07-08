package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.audio.Player
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.springframework.stereotype.Component

@Component
class ControlsCommand(
    private val panelRenderer: PlayerPanelRenderer
) : Command("controls", "panel", "show") {
    override suspend fun CommandContext.invoke() {
        player.enablePersistentControls(channel)
        if (isSlashCommand) reply("Persistent player panel enabled in ${channel.asMention}.")
    }

    fun buildMessage(player: Player): MessageCreateData = panelRenderer.build(player)

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Shows a persistent player control panel with buttons and a menu.")
    }
}

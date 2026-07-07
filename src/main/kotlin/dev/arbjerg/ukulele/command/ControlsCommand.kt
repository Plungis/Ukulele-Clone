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
) : Command("controls", "panel") {
    override suspend fun CommandContext.invoke() {
        player.lastChannel = channel
        player.showOrUpdateControls()
        if (isSlashCommand) reply("Player panel refreshed.")
    }

    fun buildMessage(player: Player): MessageCreateData = panelRenderer.build(player)

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Shows a player control panel with buttons and a menu.")
    }
}

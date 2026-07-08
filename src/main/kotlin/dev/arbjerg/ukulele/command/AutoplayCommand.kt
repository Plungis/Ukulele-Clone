package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component

@Component
class AutoplayCommand : Command("autoplay", "auto") {
    override suspend fun CommandContext.invoke() {
        player.isAutoplaying = when (argumentText.lowercase()) {
            "on", "true", "enable", "enabled" -> true
            "off", "false", "disable", "disabled" -> false
            else -> !player.isAutoplaying
        }

        player.showOrUpdateControls()
        reply("Autoplay is now **${if (player.isAutoplaying) "on" else "off"}**.")
    }

    override fun HelpContext.provideHelp() {
        addUsage("[on|off]")
        addDescription("Toggles autoplay for related tracks when the queue ends.")
    }
}

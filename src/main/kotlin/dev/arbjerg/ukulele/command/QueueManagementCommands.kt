package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component

@Component
class RemoveCommand : Command("remove", "rm") {
    override suspend fun CommandContext.invoke() {
        val index = argumentText.toIntOrNull() ?: return replyHelp()
        val removed = player.remove(index) ?: return reply("I couldn't find a track at position `$index`.")
        reply("Removed `${removed.info.title}`.")
    }

    override fun HelpContext.provideHelp() {
        addUsage("<position>")
        addDescription("Removes a track from the queue.")
    }
}

@Component
class MoveCommand : Command("move", "mv") {
    override suspend fun CommandContext.invoke() {
        val args = argumentText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val from = args.getOrNull(0)?.toIntOrNull() ?: return replyHelp()
        val to = args.getOrNull(1)?.toIntOrNull() ?: return replyHelp()
        val moved = player.move(from, to) ?: return reply("I can only move upcoming queue tracks. The current track stays put.")
        reply("Moved `${moved.info.title}` from `$from` to `$to`.")
    }

    override fun HelpContext.provideHelp() {
        addUsage("<from> <to>")
        addDescription("Moves an upcoming queue track to another position.")
    }
}

@Component
class ClearCommand : Command("clear", "clearqueue") {
    override suspend fun CommandContext.invoke() {
        val cleared = player.clearUpcoming()
        reply("Cleared **$cleared** upcoming tracks.")
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Clears upcoming tracks without stopping the current song.")
    }
}

@Component
class JumpCommand : Command("jump", "goto") {
    override suspend fun CommandContext.invoke() {
        val index = argumentText.toIntOrNull() ?: return replyHelp()
        val jumped = player.jump(index) ?: return reply("I couldn't find a track at position `$index`.")
        reply("Jumped to `${jumped.info.title}`.")
    }

    override fun HelpContext.provideHelp() {
        addUsage("<position>")
        addDescription("Jumps to a queued track.")
    }
}

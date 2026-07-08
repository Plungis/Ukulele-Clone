package dev.arbjerg.ukulele.command

import dev.arbjerg.ukulele.jda.CommandContext
import net.dv8tion.jda.api.Permission

fun CommandContext.ensureVoiceChannel(): Boolean {
    val ourVc = guild.selfMember.voiceState?.channel
    val theirVc = invoker.voiceState?.channel

    if (ourVc == null && theirVc == null) {
        reply("You need to be in a voice channel.")
        return false
    }

    if (ourVc != theirVc && theirVc != null) {
        val canTalk = selfMember.hasPermission(Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)
        if (!canTalk) {
            reply("I need permission to connect and speak in ${theirVc.name}.")
            return false
        }

        guild.audioManager.openAudioConnection(theirVc)
        guild.audioManager.sendingHandler = player
        return true
    }

    return ourVc != null
}

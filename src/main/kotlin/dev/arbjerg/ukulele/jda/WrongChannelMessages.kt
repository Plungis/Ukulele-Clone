package dev.arbjerg.ukulele.jda

import kotlin.random.Random

object WrongChannelMessages {
    private val messages = listOf(
        "Congrats, you found the play button. Next quest: finding the channel it belongs in",
        "Your song choice might be good. Your ability to read channel names? Questionable.",
        "Wrong channel again? At this point your toes might navigate Discord better.",
        "The music or commands channel is right there. It's not hiding. It didn't put on a disguise.",
        "This song has been reported for loitering outside the music channel.",
        "Amazing. Out of all possible channels, you picked the one specifically not for designated for music.",
        "Wrong channel detected. Deploying disappointed foot taps.",
        "This is why we can't have nice playlists.",
        "Your music wandered away. Please put a leash on it and bring it back to the designated music channel.",
        "Bold choice ignoring the designated music channel. Wrong choice, but bold.",
        "Please relocate your tunes before we replace your song with 12 hours of squeaky shoes."
    )

    fun pick(channelMention: String): String {
        return "(Wrong Channel) ${messages[Random.nextInt(messages.size)]}\nUse $channelMention."
    }
}

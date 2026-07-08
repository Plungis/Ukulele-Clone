package dev.arbjerg.ukulele.audio

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

var AudioTrack.meta: TrackMeta
    get() = this.userData as TrackMeta
    set(data) { this.userData = data }

open class TrackMeta

data class TtsTrackMeta(
    val text: String,
    val requestedBy: String,
    val filePath: String? = null
) : TrackMeta()

val AudioTrack.displayTitle: String
    get() = (userData as? TtsTrackMeta)?.let { "TTS: ${it.text.preview(80)}" } ?: info.title

val AudioTrack.displayAuthor: String
    get() = (userData as? TtsTrackMeta)?.let { "Requested by ${it.requestedBy}" } ?: info.author

val AudioTrack.displayUri: String
    get() = if (userData is TtsTrackMeta) "" else info.uri

fun String.preview(maxLength: Int): String {
    val compact = replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength - 3) + "..."
}

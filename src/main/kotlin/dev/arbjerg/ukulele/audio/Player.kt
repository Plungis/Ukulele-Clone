package dev.arbjerg.ukulele.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.arbjerg.ukulele.command.NowPlayingCommand
import dev.arbjerg.ukulele.command.PlayerPanelRenderer
import dev.arbjerg.ukulele.config.BotProps
import dev.arbjerg.ukulele.data.GuildProperties
import dev.arbjerg.ukulele.data.GuildPropertiesService
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class Player(val beans: Beans, private val guild: Guild, guildProperties: GuildProperties) : AudioEventAdapter(), AudioSendHandler {
    @Component
    class Beans(
            val apm: AudioPlayerManager,
            val guildProperties: GuildPropertiesService,
            val nowPlayingCommand: NowPlayingCommand,
            val panelRenderer: PlayerPanelRenderer,
            val botProps: BotProps
    )

    private val guildId = guildProperties.guildId
    private val queue = TrackQueue()
    private val player = beans.apm.createPlayer().apply {
        addListener(this@Player)
        volume = guildProperties.volume
    }
    private val buffer = ByteBuffer.allocate(1024)
    private val frame: MutableAudioFrame = MutableAudioFrame().apply { setBuffer(buffer) }
    private val log: Logger = LoggerFactory.getLogger(Player::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "ukulele-player-$guildId").apply { isDaemon = true }
    }
    private var inactivityDisconnect: ScheduledFuture<*>? = null
    private var emptyChannelDisconnect: ScheduledFuture<*>? = null
    private var controlsRefresh: ScheduledFuture<*>? = null
    private var controlsBump: ScheduledFuture<*>? = null
    private var currentTrackStartedAt: Long? = null
    private var currentTrackPlayedMillis: Long = 0
    private var sessionPlayedMillis: Long = 0
    private val playedTracks = ArrayDeque<PlayedTrack>()

    var volume: Int
        get() = player.volume
        set(value) {
            player.volume = value
            beans.guildProperties.transform(guildId) {
                it.volume = player.volume
            }.subscribe()
        }

    val tracks: List<AudioTrack> get() {
        val tracks = queue.tracks.toMutableList()
        player.playingTrack?.let { tracks.add(0, it) }
        return tracks
    }

    val remainingDuration: Long get() {
        var duration = 0L
        if (player.playingTrack != null && !player.playingTrack.info.isStream)
            player.playingTrack?.let { duration = it.info.length - it.position }
        return duration + queue.duration
    }

    val isPaused : Boolean
        get() = player.isPaused

    val sessionPlayedDuration: Long
        get() = sessionPlayedMillis + currentTrackPlaybackMillis()

    val history: List<PlayedTrack>
        get() = playedTracks.toList()

    var isRepeating : Boolean = false
    var isAutoplaying : Boolean = false

    var lastChannel: TextChannel? = null
    private var configuredPanelChannelId: Long? = guildProperties.panelChannelId
    private var controlsChannel: TextChannel? = configuredPanelChannelId?.let { guild.getTextChannelById(it) }
    private var controlsMessageId: Long? = guildProperties.panelMessageId
    private var controlsPersistent: Boolean = controlsChannel != null

    val isConnected: Boolean
        get() = guild.audioManager.isConnected

    /**
     * @return whether or not we started playing
     */
    fun add(vararg tracks: AudioTrack): Boolean {
        queue.add(*tracks)
        markActive()
        if (player.playingTrack == null) {
            player.playTrack(queue.take()!!)
            return true
        }
        showOrUpdateControls()
        return false
    }

    fun skip(range: IntRange): List<AudioTrack> {
        markActive()
        val rangeFirst = range.first.coerceAtMost(queue.tracks.size)
        val rangeLast = range.last.coerceAtMost(queue.tracks.size)
        val skipped = mutableListOf<AudioTrack>()
        var newRange = rangeFirst .. rangeLast 
        // Skip the first track if it is stored here
        if (newRange.contains(0) && player.playingTrack != null) {
            skipped.add(player.playingTrack)
            // Reduce range if found
            newRange = 0 .. rangeLast - 1
        } else {
            newRange = newRange.first - 1 .. newRange.last - 1
        }
        if (newRange.last >= 0) skipped.addAll(queue.removeRange(newRange))
        if (skipped.firstOrNull() == player.playingTrack) {
            if(isRepeating){
                queue.add(player.playingTrack.makeClone())
            }
            player.stopTrack()
        }
        return skipped
    }

    fun pause() {
        pauseElapsedTimer()
        player.isPaused = true
        controlsRefresh?.cancel(false)
        scheduleInactivityDisconnect()
        showOrUpdateControls()
    }

    fun resume() {
        player.isPaused = false
        currentTrackStartedAt = System.currentTimeMillis()
        markActive()
        scheduleControlsRefresh()
        showOrUpdateControls()
    }

    fun shuffle() {
        queue.shuffle()
        markActive()
        showOrUpdateControls()
    }

    fun clearUpcoming(): Int {
        val count = queue.tracks.size
        queue.clear()
        showOrUpdateControls()
        return count
    }

    fun remove(index: Int): AudioTrack? {
        if (index <= 0) return null
        val removed = if (index == 1) {
            skip(0..0).firstOrNull()
        } else {
            val queueIndex = index - 2
            if (queueIndex !in queue.tracks.indices) return null
            queue.removeAt(queueIndex)
        }
        showOrUpdateControls()
        return removed
    }

    fun move(from: Int, to: Int): AudioTrack? {
        val fromIndex = from - 2
        val toIndex = to - 2
        if (from <= 1 || to <= 1) return null
        if (fromIndex !in queue.tracks.indices) return null
        val moved = queue.move(fromIndex, toIndex)
        showOrUpdateControls()
        return moved
    }

    fun jump(index: Int): AudioTrack? {
        if (index <= 0) return null
        if (index == 1) return player.playingTrack

        val queueIndex = index - 2
        if (queueIndex !in queue.tracks.indices) return null
        val selected = queue.removeAt(queueIndex)
        player.playTrack(selected)
        markActive()
        showOrUpdateControls()
        return selected
    }

    fun stop() {
        disconnect("stopped")
    }

    fun seek(position: Long) {
        player.playingTrack.position = position
        markActive()
        showOrUpdateControls()
    }

    fun onVoiceStateChanged() {
        if (guild.audioManager.connectedChannel == null) return
        if (listenersInConnectedChannel() == 0) {
            scheduleEmptyChannelDisconnect()
        } else {
            emptyChannelDisconnect?.cancel(false)
        }
    }

    fun buildSessionSummary(): String {
        val recent = history.takeLast(5).asReversed()
        return buildString {
            append("Tracks played: **${history.size}**\n")
            append("Session play time: **${dev.arbjerg.ukulele.utils.TextUtils.humanReadableTime(sessionPlayedDuration)}**")
            if (recent.isNotEmpty()) {
                append("\n\nRecently played:\n")
                recent.forEach {
                    append("**${it.title}**")
                    if (it.author.isNotBlank()) append(" - ${it.author}")
                    append(" `[${dev.arbjerg.ukulele.utils.TextUtils.humanReadableTime(it.playedMillis)}]`\n")
                }
            }
        }
    }

    fun showOrUpdateControls() {
        val channel = lastChannel ?: controlsChannel ?: return
        val message = beans.panelRenderer.build(this)
        val editData = MessageEditBuilder.fromCreateData(message).build()
        val messageId = controlsMessageId

        controlsChannel = channel
        if (controlsPersistent && controlsMessageId == null) {
            repostControlsNow()
            return
        }

        if (messageId == null) {
            channel.sendMessage(message).queue { sent ->
                controlsMessageId = sent.idLong
                controlsChannel = sent.channel.asTextChannel()
            }
            return
        }

        channel.retrieveMessageById(messageId).queue({ existing ->
            existing.editMessage(editData).queue(null) {
                sendFreshControls(channel, message)
            }
        }, {
            sendFreshControls(channel, message)
        })
    }

    fun enablePersistentControls(channel: TextChannel) {
        controlsPersistent = true
        lastChannel = channel
        controlsChannel = channel
        configuredPanelChannelId = channel.idLong
        beans.guildProperties.transform(guildId) {
            it.panelChannelId = channel.idLong
        }.subscribe()
        repostControlsNow()
    }

    fun restorePersistentControls(channel: TextChannel) {
        controlsPersistent = true
        lastChannel = channel
        controlsChannel = channel
    }

    fun repostPersistentControls(channel: TextChannel? = null) {
        if (channel != null) {
            lastChannel = channel
            controlsChannel = channel
        } else {
            restoreConfiguredPanelChannel()
        }
        if (!controlsPersistent) return
        repostControlsNow()
    }

    fun bumpPersistentControls(channel: TextChannel? = null) {
        if (!controlsPersistent) restoreConfiguredPanelChannel()
        if (!controlsPersistent) return
        if (channel != null && controlsChannel?.idLong != channel.idLong) return
        controlsBump?.cancel(false)
        controlsBump = scheduler.schedule({
            repostControlsNow()
        }, PANEL_BUMP_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun restoreConfiguredPanelChannel() {
        val panelChannelId = configuredPanelChannelId ?: return
        val channel = guild.getTextChannelById(panelChannelId) ?: return
        restorePersistentControls(channel)
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        currentTrackStartedAt = System.currentTimeMillis()
        currentTrackPlayedMillis = 0
        markActive()
        scheduleControlsRefresh()
        showOrUpdateControls()
        if (beans.botProps.announceTracks) {
            lastChannel?.sendMessage(beans.nowPlayingCommand.buildMessage(track, player.isPaused))?.queue()
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        controlsRefresh?.cancel(false)
        recordPlayed(track)
        if (isRepeating && endReason.mayStartNext) {
            queue.add(track.makeClone())
        }
        val new = queue.take() ?: run {
            if (isAutoplaying && endReason.mayStartNext) {
                loadAutoplay(track)
                return
            }
            scheduleInactivityDisconnect()
            showOrUpdateControls()
            return
        }
        player.playTrack(new)
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        log.error("Track exception", exception)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        log.error("Track $track got stuck!")
    }

    override fun canProvide(): Boolean {
        return player.provide(frame)
    }

    override fun provide20MsAudio(): ByteBuffer {
        // flip to make it a read buffer
        (buffer as Buffer).flip()
        return buffer
    }

    override fun isOpus() = true

    private fun markActive() {
        inactivityDisconnect?.cancel(false)
        onVoiceStateChanged()
    }

    private fun scheduleInactivityDisconnect() {
        inactivityDisconnect?.cancel(false)
        val delay = beans.botProps.inactiveDisconnectMinutes
        if (delay <= 0 || !guild.audioManager.isConnected) return
        inactivityDisconnect = scheduler.schedule({
            if (player.playingTrack == null || player.isPaused) disconnect("inactive")
        }, delay, TimeUnit.MINUTES)
    }

    private fun scheduleEmptyChannelDisconnect() {
        emptyChannelDisconnect?.cancel(false)
        val delay = beans.botProps.emptyChannelDisconnectMinutes
        if (delay <= 0 || !guild.audioManager.isConnected) return
        emptyChannelDisconnect = scheduler.schedule({
            if (listenersInConnectedChannel() == 0) disconnect("empty voice channel")
        }, delay, TimeUnit.MINUTES)
    }

    private fun disconnect(reason: String) {
        queue.clear()
        player.stopTrack()
        currentTrackStartedAt = null
        currentTrackPlayedMillis = 0
        inactivityDisconnect?.cancel(false)
        emptyChannelDisconnect?.cancel(false)
        controlsRefresh?.cancel(false)
        controlsBump?.cancel(false)
        if (guild.audioManager.isConnected) {
            guild.audioManager.closeAudioConnection()
            log.info("Disconnected player for guild {}: {}", guildId, reason)
        }
        showOrUpdateControls()
    }

    private fun sendFreshControls(channel: TextChannel, message: net.dv8tion.jda.api.utils.messages.MessageCreateData) {
        channel.sendMessage(message).queue { sent ->
            controlsMessageId = sent.idLong
            controlsChannel = sent.channel.asTextChannel()
            if (controlsPersistent) {
                beans.guildProperties.transform(guildId) {
                    it.panelChannelId = sent.channel.idLong
                    it.panelMessageId = sent.idLong
                }.subscribe()
            }
        }
    }

    private fun repostControlsNow() {
        val channel = controlsChannel ?: lastChannel ?: return
        val message = beans.panelRenderer.build(this)
        val messageId = controlsMessageId

        controlsChannel = channel
        if (messageId == null) {
            sendFreshControls(channel, message)
            return
        }

        channel.deleteMessageById(messageId).queue({
            sendFreshControls(channel, message)
        }, {
            sendFreshControls(channel, message)
        })
    }

    private fun scheduleControlsRefresh() {
        controlsRefresh?.cancel(false)
        controlsRefresh = scheduler.scheduleAtFixedRate({
            if (player.playingTrack == null || player.isPaused) {
                controlsRefresh?.cancel(false)
                return@scheduleAtFixedRate
            }

            showOrUpdateControls()
        }, PANEL_REFRESH_SECONDS, PANEL_REFRESH_SECONDS, TimeUnit.SECONDS)
    }

    private fun loadAutoplay(seed: AudioTrack) {
        val author = seed.info.author.takeIf { it.isNotBlank() && it != "unknown" }.orEmpty()
        val query = listOf(author, seed.info.title, "similar songs")
            .filter { it.isNotBlank() }
            .joinToString(" ")

        beans.apm.loadItem("ytsearch:$query", object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                player.playTrack(track)
                lastChannel?.sendMessage("Autoplay picked `${track.info.title}`.")?.queue()
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                val track = playlist.tracks.firstOrNull { it.info.uri != seed.info.uri }
                    ?: playlist.tracks.firstOrNull()
                if (track == null) {
                    scheduleInactivityDisconnect()
                    showOrUpdateControls()
                    return
                }
                trackLoaded(track)
            }

            override fun noMatches() {
                scheduleInactivityDisconnect()
                showOrUpdateControls()
            }

            override fun loadFailed(exception: FriendlyException) {
                log.warn("Autoplay lookup failed", exception)
                scheduleInactivityDisconnect()
                showOrUpdateControls()
            }
        })
    }

    private fun listenersInConnectedChannel(): Int {
        val channel = guild.audioManager.connectedChannel ?: return 0
        return channel.members.count { !it.user.isBot }
    }

    private fun recordPlayed(track: AudioTrack) {
        pauseElapsedTimer()
        val playedMillis = if (track.info.isStream) currentTrackPlayedMillis else track.position.coerceAtLeast(currentTrackPlayedMillis)
        sessionPlayedMillis += playedMillis
        currentTrackStartedAt = null
        currentTrackPlayedMillis = 0
        playedTracks.addLast(PlayedTrack(track.info.title, track.info.author, track.info.uri, playedMillis))
        while (playedTracks.size > MAX_HISTORY) playedTracks.removeFirst()
    }

    private fun currentTrackPlaybackMillis(): Long {
        return currentTrackPlayedMillis + currentTrackElapsed()
    }

    private fun pauseElapsedTimer() {
        currentTrackPlayedMillis += currentTrackElapsed()
        currentTrackStartedAt = null
    }

    private fun currentTrackElapsed(): Long {
        val startedAt = currentTrackStartedAt ?: return 0
        return (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
    }

    data class PlayedTrack(
        val title: String,
        val author: String,
        val uri: String,
        val playedMillis: Long
    )

    private companion object {
        const val MAX_HISTORY = 25
        const val PANEL_REFRESH_SECONDS = 5L
        const val PANEL_BUMP_DELAY_MILLIS = 750L
    }
}

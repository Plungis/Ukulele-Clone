package dev.arbjerg.ukulele.audio

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers.registerLocalSource
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers.registerRemoteSources
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver
import com.github.topi314.lavasrc.spotify.SpotifySourceManager
import dev.arbjerg.ukulele.config.BotProps
import dev.lavalink.youtube.YoutubeAudioSourceManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LavaplayerConfig {
    @Bean
    fun playerManager(botProps: BotProps): AudioPlayerManager {
        val apm = DefaultAudioPlayerManager()

        // Add the new YoutubeAudioSourceManager
        apm.registerSourceManager(YoutubeAudioSourceManager(true))
        apm.registerSourceManager(
            SpotifySourceManager(
                botProps.spotifyClientId.ifBlank { null },
                botProps.spotifyClientSecret.ifBlank { null },
                botProps.spotifySpDc.ifBlank { null },
                botProps.spotifyCountryCode,
                { apm },
                DefaultMirroringAudioTrackResolver(SPOTIFY_PROVIDERS)
            )
        )

        // Then add the rest, while excluding the legacy `YoutubeAudioSourceManager`
        @Suppress("DEPRECATION")
        registerRemoteSources(
            apm,
            MediaContainerRegistry.DEFAULT_REGISTRY,
            com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager::class.java
        )
        registerLocalSource(apm)

        return apm
    }

    private companion object {
        val SPOTIFY_PROVIDERS = arrayOf(
            "ytsearch:\"%ISRC%\"",
            "ytsearch:%QUERY%"
        )
    }
}

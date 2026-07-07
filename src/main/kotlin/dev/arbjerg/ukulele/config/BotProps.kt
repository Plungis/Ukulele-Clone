package dev.arbjerg.ukulele.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("config")
class BotProps(
        var token: String = "",
        var shards: Int = 1,
        var prefix: String = "::",
        var database: String = "./database",
        var game: String = "",
        var trackDurationLimit: Int = 0,
        var announceTracks: Boolean = false,
        var spotifyClientId: String = "",
        var spotifyClientSecret: String = "",
        var spotifySpDc: String = "",
        var spotifyCountryCode: String = "US",
        var inactiveDisconnectMinutes: Long = 15,
        var emptyChannelDisconnectMinutes: Long = 5
)

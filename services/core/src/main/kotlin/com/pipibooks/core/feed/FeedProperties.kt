package com.pipibooks.core.feed

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("feed")
data class FeedProperties(
    @DefaultValue val weights: Weights = Weights(),
    @DefaultValue("14") val freshnessHalfLifeDays: Int = 14,
    @DefaultValue("2000") val candidateLimit: Int = 2000,
    @DefaultValue("500") val sessionSize: Int = 500,
    @DefaultValue("20") val pageSize: Int = 20,
    @DefaultValue("30m") val sessionTtl: Duration = DEFAULT_SESSION_TTL,
) {
    private companion object {
        val DEFAULT_SESSION_TTL: Duration = Duration.ofMinutes(30)
    }

    data class Weights(
        @DefaultValue("3.0") val interest: Double = 3.0,
        @DefaultValue("2.0") val shelf: Double = 2.0,
        @DefaultValue("1.0") val popularity: Double = 1.0,
        @DefaultValue("1.5") val freshness: Double = 1.5,
        @DefaultValue("0.5") val random: Double = 0.5,
    )
}

@Configuration
@EnableConfigurationProperties(FeedProperties::class)
class FeedConfig

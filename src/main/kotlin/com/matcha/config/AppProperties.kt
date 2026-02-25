package com.matcha.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val match: MatchProperties = MatchProperties(),
    val email: EmailProperties = EmailProperties(),
) {
    data class MatchProperties(
        @DefaultValue("75") val scoreThreshold: Int = 75,
        @DefaultValue("12000") val resumeCharLimit: Int = 12000,
        @DefaultValue("12000") val jdCharLimit: Int = 12000,
    )

    data class EmailProperties(
        @DefaultValue("noreply@example.com") val from: String = "noreply@example.com",
        @DefaultValue("you@example.com") val to: String = "you@example.com",
        @DefaultValue("[Matcha]") val subjectPrefix: String = "[Matcha]",
    )
}

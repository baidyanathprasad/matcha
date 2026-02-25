package com.matcha.config

import org.apache.tika.Tika
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppConfig {
    /**
     * Single shared Tika instance – thread-safe and expensive to initialize.
     * Registered as a Spring Bean so it can be injected where needed.
     */
    @Bean
    fun tika(): Tika = Tika()
}

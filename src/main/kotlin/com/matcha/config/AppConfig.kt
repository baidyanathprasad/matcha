package com.matcha.config

import org.apache.tika.Tika
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppConfig {
    /**
     * Single shared Tika instance – thread-safe and expensive to initialize.
     * Registered as a Spring Bean so it can be injected where needed.
     */
    @Bean
    fun tika(): Tika = Tika()

    /**
     * CORS configuration to allow frontend requests.
     * Allows localhost and same-origin requests.
     */
    @Bean
    fun corsConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry
                    .addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000", "http://localhost:8080", "http://127.0.0.1:8080")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
            }
        }
}

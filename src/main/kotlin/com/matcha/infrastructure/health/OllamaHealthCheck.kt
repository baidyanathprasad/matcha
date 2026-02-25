package com.matcha.infrastructure.health

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.HttpURLConnection
import java.net.URL

/**
 * REST endpoint for checking Ollama's health status.
 *
 * Usage:
 *   GET /api/v1/health/ollama
 */
@RestController
@RequestMapping("/api/v1/health")
class OllamaHealthCheck {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ollamaUrl = "http://localhost:11434/api/tags"
    private val timeout = 3000 // 3 seconds

    @GetMapping("/ollama", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun checkOllamaHealth(): ResponseEntity<Map<String, Any>> =
        try {
            val connection = URL(ollamaUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            connection.disconnect()

            val body =
                when {
                    responseCode == 200 -> {
                        log.info("Ollama health check passed")
                        mapOf(
                            "status" to "UP",
                            "service" to "ollama",
                            "url" to ollamaUrl,
                            "responseCode" to responseCode,
                        )
                    }

                    responseCode >= 500 -> {
                        log.warn("Ollama server error ({})", responseCode)
                        mapOf(
                            "status" to "DOWN",
                            "service" to "ollama",
                            "url" to ollamaUrl,
                            "responseCode" to responseCode,
                            "reason" to "Server error",
                        )
                    }

                    else -> {
                        log.warn("Ollama unexpected response code: {}", responseCode)
                        mapOf(
                            "status" to "DEGRADED",
                            "service" to "ollama",
                            "url" to ollamaUrl,
                            "responseCode" to responseCode,
                        )
                    }
                }
            ResponseEntity.ok(body)
        } catch (ex: Exception) {
            log.warn("Ollama health check failed: {}", ex.message)
            val body =
                mapOf(
                    "status" to "DOWN",
                    "service" to "ollama",
                    "url" to ollamaUrl,
                    "reason" to (ex.message ?: ex.javaClass.simpleName),
                )
            ResponseEntity.status(503).body(body)
        }
}

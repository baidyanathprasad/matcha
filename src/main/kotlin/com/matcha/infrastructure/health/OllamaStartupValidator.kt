package com.matcha.infrastructure.health

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URL

/**
 * Startup validator that checks Ollama availability when the application starts.
 *
 * This component logs helpful information if Ollama is not accessible.
 * It does NOT fail startup to allow graceful degradation.
 */
@Component
class OllamaStartupValidator {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ollamaUrl = "http://localhost:11434"
    private val tagsUrl = "$ollamaUrl/api/tags"
    private val timeout = 2000 // 2 seconds

    @EventListener(ApplicationReadyEvent::class)
    fun validateOllamaOnStartup() {
        log.info("Checking Ollama availability at $ollamaUrl...")

        try {
            val connection = URL(tagsUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            connection.disconnect()

            when (responseCode) {
                200 -> {
                    log.info("✓ Ollama is accessible and running")
                }

                500 -> {
                    log.warn("⚠ Ollama responded with server error (500)")
                    log.warn("  Try restarting Ollama: ollama serve")
                }

                else -> {
                    log.warn("⚠ Ollama responded with unexpected code: $responseCode")
                }
            }
        } catch (ex: Exception) {
            logOllamaUnavailable(ex)
        }
    }

    private fun logOllamaUnavailable(ex: Exception) {
        val message = ex.message ?: ex.javaClass.simpleName

        log.warn("⚠ Ollama is NOT accessible at $ollamaUrl")
        log.warn("")
        log.warn("┌─────────────────────────────────────────────────────────────────┐")
        log.warn("│ OLLAMA NOT RUNNING - Requests will fail                         │")
        log.warn("├─────────────────────────────────────────────────────────────────┤")
        log.warn("│ To fix this, run one of the following commands:                 │")
        log.warn("│                                                                 │")
        log.warn("│ 1. Start Ollama (if installed):                                 │")
        log.warn("│    $ ollama serve                                               │")
        log.warn("│                                                                 │")
        log.warn("│ 2. Pull required model (llama3.2):                              │")
        log.warn("│    $ ollama pull llama3.2                                       │")
        log.warn("│                                                                 │")
        log.warn("│ 3. If Ollama is on a different host, update:                    │")
        log.warn("│    spring.ai.ollama.base-url in application.yml                 │")
        log.warn("│                                                                 │")
        log.warn("│ 4. Verify connectivity:                                         │")
        log.warn("│    $ curl -s $ollamaUrl/api/tags                 │")
        log.warn("│                                                                 │")
        log.warn("│ Error: $message")
        log.warn("└─────────────────────────────────────────────────────────────────┘")
        log.warn("")
    }
}

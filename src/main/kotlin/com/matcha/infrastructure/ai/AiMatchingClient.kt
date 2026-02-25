package com.matcha.infrastructure.ai

import com.matcha.config.AppProperties
import com.matcha.domain.model.MatchResult
import com.matcha.exception.AiParsingException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Infrastructure adapter that communicates with the local Ollama LLM via
 * Spring AI's [ChatClient] fluent API.
 *
 * Responsibilities:
 *   - Build the comparison prompt (résumé + JD text + format instructions).
 *   - Call the configured Ollama model (llama3.2 by default).
 *   - Deserialize the JSON response into a [MatchResult] domain object.
 *
 * The [BeanOutputConverter] appends a JSON-schema block to the prompt so the
 * model knows exactly what structure to emit, then handles deserialization.
 * The temperature is kept low (0.2) in application.yml for deterministic JSON output.
 */
@Component
class AiMatchingClient(
    chatClientBuilder: ChatClient.Builder,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = chatClientBuilder.build()
    private val converter = BeanOutputConverter(MatchResult::class.java)

    /**
     * Send résumé and JD text to the LLM and return the structured assessment.
     *
     * @param resumeText Plain text extracted from the PDF résumé.
     * @param jobText    Plain text scraped from the job posting.
     * @return [MatchResult] populated by the LLM.
     * @throws AiParsingException if the model response cannot be deserialize.
     */
    fun compare(
        resumeText: String,
        jobText: String,
    ): MatchResult {
        // Truncate inputs to stay within llama3.2's context window
        val resume = resumeText.take(props.match.resumeCharLimit)
        val jd = jobText.take(props.match.jdCharLimit)

        log.info("Sending to Ollama – résumé={} chars, JD={} chars", resume.length, jd.length)

        val prompt = buildPrompt(resume, jd)

        // Retry loop for transient network / I/O issues
        var rawResponse: String? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                rawResponse =
                    client
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
                break
            } catch (ex: Throwable) {
                val root = findRootCause(ex)
                val rootMsg = (root.message ?: "").lowercase()

                // Fail fast for non-transient "configuration / environment" errors (no amount of retry helps)
                if (isModelNotFound(rootMsg) || rootMsg.contains("model") && rootMsg.contains("not found")) {
                    val err = buildErrorMessage(ex)
                    log.error("Ollama model not available: {}", err)
                    throw AiParsingException(err, ex)
                }

                // If it's a clear connectivity error, build an actionable message and abort
                if (root is ConnectException || root is UnknownHostException || root is SocketTimeoutException ||
                    (root.message?.contains("Connection refused", ignoreCase = true) == true)
                ) {
                    val err = buildErrorMessage(ex)
                    log.error("Ollama connectivity error (attempt {}/{}): {}", attempt, maxAttempts, err)
                    throw AiParsingException(err, ex)
                }

                // For other errors, log and retry with exponential backoff
                log.warn("Transient error calling Ollama (attempt {}/{}): {}", attempt, maxAttempts, ex.message)
                if (attempt == maxAttempts) {
                    val err = buildErrorMessage(ex)
                    log.error("Final attempt failed. {}", err, ex)
                    throw AiParsingException(err, ex)
                }

                try {
                    Thread.sleep(250L * (1 shl (attempt - 1))) // 250ms, 500ms, 1000ms
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw AiParsingException("Interrupted while waiting to retry Ollama call", ex)
                }
            }
        }

        log.debug("Raw AI response:\n{}", rawResponse)

        return runCatching { converter.convert(rawResponse!!) }
            .getOrElse { ex ->
                throw AiParsingException(
                    "Could not deserialize AI response into MatchResult. " +
                        "Raw response was: ${rawResponse?.take(300)}",
                    ex,
                )
            }!!
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildErrorMessage(ex: Throwable): String {
        val ollamaUrl = "http://localhost:11434"
        val rootCause = findRootCause(ex)
        val message = rootCause.message ?: rootCause.javaClass.simpleName
        val lower = message.lowercase()

        return when {
            // ... existing code ...

            // "model not found" (common when the configured model tag isn't pulled/available)
            isModelNotFound(lower) -> {
                "Ollama reports the requested model is not available.\n" +
                    "Details: $message\n" +
                    "Fix options:\n" +
                    "  1) Choose a model that exists on the Ollama server (check: $ollamaUrl/api/tags)\n" +
                    "  2) Or make the model available on that Ollama instance, then retry\n" +
                    "Tip: ensure your Spring AI setting spring.ai.ollama.chat.options.model matches an entry from /api/tags"
            }

            // HTTP error (e.g., 404, 500)
            lower.contains("404") -> {
                "Ollama returned 404. In many cases this means the model name is wrong or missing on the server.\n" +
                    "Details: $message\n" +
                    "Verify available models: curl -s $ollamaUrl/api/tags\n" +
                    "Then set spring.ai.ollama.chat.options.model to one of those names."
            }

            lower.contains("500") -> {
                "Ollama server error (500). The service may be misconfigured or the model may be missing.\n" +
                    "Verify models: curl -s $ollamaUrl/api/tags"
            }

            // ... existing code ...

            else -> {
                "Ollama call failed: $message\n" +
                    "Expected Ollama at: $ollamaUrl\n" +
                    "Verify available models: curl -s $ollamaUrl/api/tags\n" +
                    "Ensure spring.ai.ollama.chat.options.model matches an available model."
            }
        }
    }

    private fun isModelNotFound(lowerMessage: String): Boolean {
        // Typical Ollama error payload ends up in exception message like:
        // {"error":"model 'X' not found"}
        return lowerMessage.contains("model") &&
            lowerMessage.contains("not found") &&
            (lowerMessage.contains("{\"error\"") || lowerMessage.contains("error"))
    }

    private fun findRootCause(ex: Throwable): Throwable {
        var current = ex
        while (current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private fun buildPrompt(
        resume: String,
        jd: String,
    ): String =
        """
        You are an expert technical recruiter and résumé analyst.

        Analyse the RESUME and JOB DESCRIPTION below and produce a compatibility assessment.

        ## RESUME
        $resume

        ## JOB DESCRIPTION
        $jd

        ## RULES
        - Score the match 0 (no overlap) to 100 (perfect match).
        - List every skill / technology / qualification present in BOTH documents under matchedSkills.
        - List every JD requirement missing or weak in the résumé under gaps.
        - Write a concise 2–3 sentence matchReason explaining the score.
        - Respond with VALID JSON ONLY. No markdown fences, no preamble, no trailing text.

        ${converter.format}
        """.trimIndent()
}

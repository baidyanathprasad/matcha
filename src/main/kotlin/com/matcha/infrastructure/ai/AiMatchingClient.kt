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
     * @throws AiParsingException if the model response cannot be deserialized.
     */
    fun compare(
        resumeText: String,
        jobText: String,
    ): MatchResult {
        // Truncate inputs to stay within a context window while preserving more context for better matching
        val resume = resumeText.take(props.match.resumeCharLimit)
        val jd = jobText.take(props.match.jdCharLimit)

        log.info(
            "Sending to Ollama – résumé={}/{} chars, JD={}/{} chars (limits: resume={}, jd={})",
            resume.length,
            resumeText.length,
            jd.length,
            jobText.length,
            props.match.resumeCharLimit,
            props.match.jdCharLimit,
        )

        // Warn if input was truncated
        if (resume.length < resumeText.length) {
            log.warn(
                "Resume was truncated: {} chars removed (original: {}, limit: {})",
                resumeText.length - resume.length,
                resumeText.length,
                props.match.resumeCharLimit,
            )
        }
        if (jd.length < jobText.length) {
            log.warn(
                "JD was truncated: {} chars removed (original: {}, limit: {})",
                jobText.length - jd.length,
                jobText.length,
                props.match.jdCharLimit,
            )
        }

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

                // Fail fast for non-transient "configuration / environment" errors
                if ((isModelNotFound(rootMsg)) || (rootMsg.contains("model") && rootMsg.contains("not found"))) {
                    val err = buildErrorMessage(ex)
                    log.error("Ollama model not available: {}", err)
                    throw AiParsingException(err, ex)
                }

                // If it's a clear connectivity error, build an actionable message and abort
                if ((root is ConnectException || root is UnknownHostException || root is SocketTimeoutException) ||
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

        // Attempt to fix incomplete JSON if needed
        val fixedResponse = fixIncompleteJson(rawResponse)
        log.debug("Fixed AI response:\n{}", fixedResponse)
        if (fixedResponse != rawResponse) {
            log.warn("Response was incomplete; attempted to fix JSON")
        }

        // Try parsing, fallback to extracting JSON object if it fails
        return runCatching { converter.convert(fixedResponse) }
            .getOrElse { ex ->
                // Fallback: try to extract JSON object from response and parse again
                val fallbackJson = extractJsonObject(fixedResponse)
                log.warn("Fallback: attempting to parse extracted JSON object")
                runCatching { converter.convert(fallbackJson) }
                    .getOrElse { ex2 ->
                        log.error("AI response parsing failed. Raw: {}\nFixed: {}\nFallback: {}", rawResponse, fixedResponse, fallbackJson)
                        throw AiParsingException(
                            "Could not deserialize AI response into MatchResult. " +
                                "Raw response was: ${fallbackJson.take(300)}",
                            ex2,
                        )
                    }
            }!!
    }

    /**
     * Attempt to fix incomplete JSON responses from Ollama.
     * If the response is missing closing brackets/braces, add them.
     * Also remove any text before first '{' and after last '}'.
     */
    private fun fixIncompleteJson(response: String?): String {
        if (response == null) return ""
        var trimmed = response.trim()

        // Remove markdown formatting if present
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```json").removePrefix("```").trim()
        }

        // Remove any text before first '{' and after last '}'
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1)
        }

        // Remove trailing commas in arrays/objects
        trimmed = trimmed.replace(Regex(",\\s*([}\\]])"), "$1")

        // Check if JSON is incomplete (missing closing braces)
        var openBraces = 0
        var openBrackets = 0
        var inString = false
        var escapeNext = false

        for (char in trimmed) {
            if (escapeNext) {
                escapeNext = false
                continue
            }
            when (char) {
                '\\' -> escapeNext = true
                '"' -> inString = !inString
                '{' -> if (!inString) openBraces++
                '}' -> if (!inString) openBraces--
                '[' -> if (!inString) openBrackets++
                ']' -> if (!inString) openBrackets--
            }
        }

        // Append missing closing brackets/braces
        var fixed = trimmed
        if (openBrackets > 0 || openBraces > 0) {
            log.warn(
                "Incomplete JSON detected: {} open brackets, {} open braces. Attempting to fix.",
                openBrackets,
                openBraces,
            )
            repeat(openBrackets) { fixed += "]" }
            repeat(openBraces) { fixed += "}" }
        }

        // Ensure JSON starts with '{' and ends with '}'
        if (!fixed.startsWith("{")) {
            val firstBrace2 = fixed.indexOf('{')
            if (firstBrace2 >= 0) fixed = fixed.substring(firstBrace2)
        }
        if (!fixed.endsWith("}")) {
            val lastBrace2 = fixed.lastIndexOf('}')
            if (lastBrace2 >= 0) fixed = fixed.substring(0, lastBrace2 + 1)
        }

        return fixed
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private fun buildErrorMessage(ex: Throwable): String {
        val ollamaUrl = "http://localhost:11434"
        val rootCause = findRootCause(ex)
        val message = rootCause.message ?: rootCause.javaClass.simpleName
        val lower = message.lowercase()

        return when {
            // "model isn't found" (common when the configured model tag isn't pulled/available)
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

            else -> {
                "Ollama call failed: $message\n" +
                    "Expected Ollama at: $ollamaUrl\n" +
                    "Verify available models: curl -s $ollamaUrl/api/tags\n" +
                    "Ensure spring.ai.ollama.chat.options.model matches an available model."
            }
        }
    }

    private fun isModelNotFound(lowerMessage: String): Boolean {
        // Typical Ollama error payload ends up in an exception message like:
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
        You are an expert technical recruiter with deep knowledge of software engineering roles.
        
        Your task: Analyze the RESUME against the JOB DESCRIPTION and produce a structured compatibility assessment.
        
        ## RESUME
        $resume
        
        ## JOB DESCRIPTION
        $jd
        
        ## MATCHING METHODOLOGY
        Score the compatibility 0-100 using this approach:
        1. Identify ALL technical skills, technologies, and tools mentioned in BOTH documents
        2. Score based on:
           - Skill overlap (how many required skills candidate has): 50 points
           - Experience level match (seniority, scope): 25 points
           - Domain expertise alignment: 15 points
           - Soft skills / leadership if mentioned: 10 points
        3. For gaps: List ONLY explicitly stated requirements missing from the résumé
        4. Match reason: Explain in 2–3 sentences WHY you gave this score, referencing specific skills
        
        ## OUTPUT FORMAT
        Respond with VALID JSON ONLY. No markdown, no explanations, no preamble. Do not add any text before or after the JSON. The JSON must start with '{' and end with '}'.
        Include these exact fields:
        - score: integer 0-100
        - matchedSkills: array of exact skill names found in both documents
        - gaps: array of exact requirements from JD missing in résumé
        - matchReason: 2-3 sentence explanation
        
        ${converter.format}
        """.trimIndent()

    // Helper to extract a JSON object from a string
    private fun extractJsonObject(response: String?): String {
        if (response == null) return ""
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        return if (firstBrace in 0..<lastBrace) {
            response.substring(firstBrace, lastBrace + 1)
        } else {
            response
        }
    }
}

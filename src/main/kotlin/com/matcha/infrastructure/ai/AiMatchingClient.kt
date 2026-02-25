package com.matcha.infrastructure.ai

import com.matcha.config.AppProperties
import com.matcha.domain.model.MatchResult
import com.matcha.exception.AiParsingException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.stereotype.Component

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
 * Temperature is kept low (0.2) in application.yml for deterministic JSON output.
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
     * @throws AiParsingException if the model response cannot be deserialised.
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

        val rawResponse =
            runCatching {
                client
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
            }.getOrElse { ex ->
                throw AiParsingException("Ollama call failed: ${ex.message}", ex)
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

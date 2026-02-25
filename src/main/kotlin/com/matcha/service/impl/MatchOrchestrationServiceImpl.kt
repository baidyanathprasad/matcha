package com.matcha.service.impl

import com.matcha.infrastructure.ai.AiMatchingClient
import com.matcha.infrastructure.mail.MailNotificationClient
import com.matcha.infrastructure.parser.ResumeParserClient
import com.matcha.infrastructure.scraper.JobScraperClient
import com.matcha.service.MatchOrchestrationService
import com.matcha.web.response.MatchResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Default implementation of [MatchOrchestrationService].
 *
 * Orchestrates the four infrastructure clients in order, then maps the
 * domain result to a [MatchResponse]. Business rules (e.g. the email
 * threshold decision) live here – not in the controller or the clients.
 */
@Service
class MatchOrchestrationServiceImpl(
    private val resumeParser:  ResumeParserClient,
    private val jobScraper:    JobScraperClient,
    private val aiClient:      AiMatchingClient,
    private val mailClient:    MailNotificationClient
) : MatchOrchestrationService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun match(resume: MultipartFile, jobUrl: String): MatchResponse {
        log.info("Starting match pipeline for jobUrl='{}'", jobUrl)

        // Step 1 – extract résumé text
        val resumeText = resumeParser.extractText(resume)
        log.debug("Résumé extracted: {} chars", resumeText.length)

        // Step 2 – scrape the job description
        val jobText = jobScraper.scrape(jobUrl)
        log.debug("JD scraped: {} chars", jobText.length)

        // Step 3 – AI comparison
        val matchResult = aiClient.compare(resumeText, jobText)
        log.info("AI match score={} for jobUrl='{}'", matchResult.score, jobUrl)

        // Step 4 – conditionally send notification email
        val emailSent = mailClient.sendIfEligible(matchResult, jobUrl)

        return MatchResponse.from(matchResult, jobUrl, emailSent)
    }
}

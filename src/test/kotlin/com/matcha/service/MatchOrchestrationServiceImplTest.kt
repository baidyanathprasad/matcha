package com.matcha.service

import com.matcha.domain.model.MatchResult
import com.matcha.infrastructure.ai.AiMatchingClient
import com.matcha.infrastructure.mail.MailNotificationClient
import com.matcha.infrastructure.parser.ResumeParserClient
import com.matcha.infrastructure.scraper.JobScraperClient
import com.matcha.service.impl.MatchOrchestrationServiceImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchOrchestrationServiceImplTest {

    private val resumeParser = mockk<ResumeParserClient>()
    private val jobScraper   = mockk<JobScraperClient>()
    private val aiClient     = mockk<AiMatchingClient>()
    private val mailClient   = mockk<MailNotificationClient>()

    private val service = MatchOrchestrationServiceImpl(
        resumeParser, jobScraper, aiClient, mailClient
    )

    private val dummyFile = MockMultipartFile(
        "resume", "resume.pdf", "application/pdf", "PDF content".toByteArray()
    )

    @Test
    fun `match - happy path - returns MatchResponse with emailSent true`() {
        val result = MatchResult(
            score         = 90,
            matchedSkills = listOf("Kotlin", "Spring Boot"),
            gaps          = listOf("Kubernetes"),
            matchReason   = "Strong backend match."
        )

        every { resumeParser.extractText(dummyFile) } returns "résumé text"
        every { jobScraper.scrape("https://example.com/job") } returns "job text"
        every { aiClient.compare("résumé text", "job text") } returns result
        every { mailClient.sendIfEligible(result, "https://example.com/job") } returns true

        val response = service.match(dummyFile, "https://example.com/job")

        assertEquals(90, response.score)
        assertEquals(listOf("Kotlin", "Spring Boot"), response.matchedSkills)
        assertTrue(response.emailSent)

        verify(exactly = 1) { mailClient.sendIfEligible(result, any()) }
    }

    @Test
    fun `match - low score - emailSent is false`() {
        val result = MatchResult(score = 40)

        every { resumeParser.extractText(dummyFile) } returns "résumé"
        every { jobScraper.scrape(any()) } returns "job"
        every { aiClient.compare(any(), any()) } returns result
        every { mailClient.sendIfEligible(result, any()) } returns false

        val response = service.match(dummyFile, "https://example.com/job")

        assertEquals(false, response.emailSent)
    }
}

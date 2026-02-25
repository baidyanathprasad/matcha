package com.matcha.web.controller

import com.matcha.service.MatchOrchestrationService
import com.matcha.web.response.MatchResponse
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MatchController::class)
class MatchControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    private lateinit var orchestrationService: MatchOrchestrationService

    @Test
    fun `GET health - returns 200 UP`() {
        mvc
            .perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `POST matches - valid request - returns 201 with MatchResponse`() {
        val mockResponse =
            MatchResponse(
                score = 85,
                matchedSkills = listOf("Kotlin"),
                gaps = listOf("Docker"),
                matchReason = "Good match.",
                emailSent = true,
                jobUrl = "https://example.com/job",
            )

        every { orchestrationService.match(any(), any()) } returns mockResponse

        val pdfFile =
            MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "PDF bytes".toByteArray(),
            )

        mvc
            .perform(
                multipart("/api/v1/matches")
                    .file(pdfFile)
                    .param("jobUrl", "https://example.com/job")
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.score").value(85))
            .andExpect(jsonPath("$.emailSent").value(true))
            .andExpect(jsonPath("$.matchedSkills[0]").value("Kotlin"))
    }

    @Test
    fun `POST matches - missing jobUrl - returns 400`() {
        val pdfFile =
            MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "PDF bytes".toByteArray(),
            )

        mvc
            .perform(
                multipart("/api/v1/matches")
                    .file(pdfFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            ).andExpect(status().isBadRequest)
    }
}

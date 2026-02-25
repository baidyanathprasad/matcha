package com.matcha.web.controller

import com.matcha.service.MatchOrchestrationService
import com.matcha.web.response.MatchResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * REST controller exposing the résumé-matching API.
 *
 * Base path: /api/v1/matches
 *
 * Endpoints:
 *   POST /api/v1/matches – upload résumé + job URL → [MatchResponse]
 *   GET /api/v1/health – liveness probe
 *
 * The controller is intentionally thin: it validates input, delegates all
 * business logic to [MatchOrchestrationService], and maps the result to an
 * HTTP response. No business logic lives here.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
class MatchController(
    private val orchestrationService: MatchOrchestrationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Matches an uploaded PDF résumé against a job posting URL.
     *
     * curl example:
     *   curl -X POST http://localhost:8080/api/v1/matches \
     *        -F "resume=@/path/to/resume.pdf"             \
     *        -F "jobUrl=https://www.linkedin.com/jobs/view/123456"
     *
     * @param resume  Multipart PDF file (≤ 20 MB).
     * @param jobUrl  Publicly accessible job posting URL.
     * @return 201 Created with [MatchResponse] body.
     */
    @PostMapping(
        "/matches",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun createMatch(
        @RequestPart("resume") resume: MultipartFile,
        @RequestParam("jobUrl")
        @NotBlank(message = "jobUrl must not be blank")
        @Pattern(regexp = "^https?://.*", message = "jobUrl must be a valid http/https URL")
        jobUrl: String,
    ): ResponseEntity<MatchResponse> {
        log.info(
            "POST /api/v1/matches – file='{}', jobUrl='{}'",
            resume.originalFilename,
            jobUrl,
        )

        val response = orchestrationService.match(resume, jobUrl)

        return ResponseEntity
            .status(201)
            .body(response)
    }

    /**
     * Simple liveness probe used by load balancers and Docker health checks.
     */
    @GetMapping("/health", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "matcha",
                "version" to "1.0.0",
            ),
        )
}

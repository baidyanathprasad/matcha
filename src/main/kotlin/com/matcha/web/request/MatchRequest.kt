package com.matcha.web.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.web.multipart.MultipartFile

/**
 * Encapsulates the multipart form data received at [POST /api/v1/matches].
 *
 * Note: Spring MVC binds multipart fields individually via @RequestPart /
 * @RequestParam, but grouping them here keeps the controller signature clean
 * and makes unit testing straightforward.
 */
data class MatchRequest(

    /** PDF résumé uploaded by the user. */
    val resume: MultipartFile,

    /**
     * Publicly accessible URL of the job posting.
     * Must be a valid http/https URL.
     */
    @field:NotBlank(message = "jobUrl must not be blank")
    @field:Pattern(
        regexp  = "^https?://.*",
        message = "jobUrl must be a valid http or https URL"
    )
    val jobUrl: String
)

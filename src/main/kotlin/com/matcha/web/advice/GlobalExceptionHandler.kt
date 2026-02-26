package com.matcha.web.advice

import com.matcha.exception.AiParsingException
import com.matcha.exception.JobScrapingException
import com.matcha.exception.ResumeParsingException
import com.matcha.web.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Centralized exception-to-HTTP mapping for the entire web layer.
 *
 * Every handler returns a consistent [ErrorResponse] envelope so API
 * consumers never have to parse ad-hoc error formats.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── Domain / application exceptions ───────────────────────────────────────
    @ExceptionHandler(ResumeParsingException::class)
    fun handleResumeParsing(
        ex: ResumeParsingException,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Resume parsing failed: {}", ex.message)
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Resume Parsing Error", ex.message, req)
    }

    @ExceptionHandler(JobScrapingException::class)
    fun handleJobScraping(
        ex: JobScrapingException,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Job scraping failed: {}", ex.message)
        return build(HttpStatus.BAD_GATEWAY, "Job Scraping Error", ex.message, req)
    }

    @ExceptionHandler(AiParsingException::class)
    fun handleAiParsing(
        ex: AiParsingException,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error("AI response parsing failed: {}", ex.message)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "AI Parsing Error", ex.message, req)
    }

    // ── Spring / infrastructure exceptions ────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleFileTooLarge(
        ex: MaxUploadSizeExceededException,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Upload rejected – file too large")
        return build(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "File Too Large",
            "Uploaded file exceeds the 20 MB size limit.",
            req,
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {}", ex.message)
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.message, req)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception on [{}] {}", req.method, req.requestURI, ex)
        return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",
            req,
        )
    }

    // ── helper ────────────────────────────────────────────────────────────────
    private fun build(
        status: HttpStatus,
        error: String,
        message: String?,
        req: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(
                ErrorResponse(
                    status = status.value(),
                    error = error,
                    message = message ?: "No details available",
                    path = req.requestURI,
                ),
            )
}

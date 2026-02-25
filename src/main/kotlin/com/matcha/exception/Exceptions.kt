package com.matcha.exception

/**
 * Thrown when an uploaded résumé file cannot be parsed or yields no text.
 */
class ResumeParsingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

/**
 * Thrown when a job posting URL cannot be scraped or yields no usable content.
 */
class JobScrapingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

/**
 * Thrown when the AI response cannot be mapped to a [com.matcha.domain.model.MatchResult].
 */
class AiParsingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

/**
 * Thrown when the e-mail dispatch fails.
 */
class EmailDispatchException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

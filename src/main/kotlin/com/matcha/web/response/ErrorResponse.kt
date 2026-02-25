package com.matcha.web.response

import java.time.Instant

/**
 * Standard error envelope returned for all non-2xx responses.
 * Follows RFC 7807 Problem Details style (simplified).
 */
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)

package com.matcha.web.response

import com.matcha.domain.model.MatchResult

/**
 * HTTP response envelope returned by [POST /api/v1/matches].
 *
 * Wraps [MatchResult] with additional metadata useful to API consumers
 * without leaking internal domain details directly.
 */
data class MatchResponse(

    /** 0–100 compatibility score. */
    val score: Int,

    /** Skills present in both the résumé and the JD. */
    val matchedSkills: List<String>,

    /** JD requirements missing or weak in the résumé. */
    val gaps: List<String>,

    /** AI-generated rationale for the score. */
    val matchReason: String,

    /** Whether a notification email was dispatched. */
    val emailSent: Boolean,

    /** The job posting URL that was evaluated. */
    val jobUrl: String
) {
    companion object {
        fun from(result: MatchResult, jobUrl: String, emailSent: Boolean) = MatchResponse(
            score         = result.score,
            matchedSkills = result.matchedSkills,
            gaps          = result.gaps,
            matchReason   = result.matchReason,
            emailSent     = emailSent,
            jobUrl        = jobUrl
        )
    }
}

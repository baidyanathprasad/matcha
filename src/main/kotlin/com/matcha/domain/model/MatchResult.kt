package com.matcha.domain.model

/**
 * Core domain model representing the AI-generated compatibility assessment
 * between a résumé and a job description.
 *
 * Used as the structured output target for Spring AI's [BeanOutputConverter].
 * All fields carry defaults so Jackson can deserialise partial AI responses safely.
 */
data class MatchResult(

    /** Compatibility score from 0 (no match) to 100 (perfect match). */
    val score: Int = 0,

    /** Skills / technologies present in BOTH the résumé and the JD. */
    val matchedSkills: List<String> = emptyList(),

    /** Requirements from the JD that are absent or weak in the résumé. */
    val gaps: List<String> = emptyList(),

    /** 2–3 sentence human-readable rationale explaining the score. */
    val matchReason: String = ""
)

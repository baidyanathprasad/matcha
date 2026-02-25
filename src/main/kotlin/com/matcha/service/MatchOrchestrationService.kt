package com.matcha.service

import com.matcha.web.response.MatchResponse
import org.springframework.web.multipart.MultipartFile

/**
 * Top-level orchestration contract for the résumé-matching workflow.
 *
 * Implementations are responsible for coordinating:
 *   1. PDF text extraction
 *   2. Job description scraping
 *   3. AI-based comparison
 *   4. Conditional email notification
 */
interface MatchOrchestrationService {
    /**
     * Execute the full matching pipeline.
     *
     * @param resume  Uploaded PDF résumé file.
     * @param jobUrl  Publicly accessible job posting URL.
     * @return Structured [MatchResponse] ready for the HTTP layer.
     */
    fun match(
        resume: MultipartFile,
        jobUrl: String,
    ): MatchResponse
}

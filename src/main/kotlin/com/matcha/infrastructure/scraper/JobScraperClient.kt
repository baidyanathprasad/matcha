package com.matcha.infrastructure.scraper

import com.matcha.exception.JobScrapingException
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Infrastructure adapter that fetches and parses job postings from public URLs.
 *
 * Scraping strategy (in order of preference):
 *   1. LinkedIn-specific CSS selectors
 *   2. Indeed-specific CSS selectors
 *   3. Common ATS / semantic HTML selectors (article, main, #content)
 *   4. Full <body> text as a last resort
 *
 * A browser-like User-Agent header is sent to avoid 403 rejections from job boards.
 */
@Component
class JobScraperClient {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

        private const val TIMEOUT_MS = 15_000

        /**
         * Ordered list of (label, cssSelector) pairs tried top-down.
         * First non-blank result wins.
         */
        private val SELECTOR_CHAIN = listOf(
            "LinkedIn"  to "div.description__text",
            "LinkedIn"  to "div.show-more-less-html__markup",
            "Indeed"    to "div#jobDescriptionText",
            "Indeed"    to "div.jobsearch-JobComponent-description",
            "ATS/Generic" to "div#content",
            "ATS/Generic" to "article",
            "ATS/Generic" to "main"
        )
    }

    /**
     * Scrape and return the plain-text job description from [url].
     *
     * @param url Publicly accessible job posting URL.
     * @return Normalised plain text of the job description.
     * @throws JobScrapingException if the page cannot be fetched or yields no text.
     */
    fun scrape(url: String): String {
        log.info("Scraping job posting: {}", url)

        val doc: Document = runCatching {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()
        }.getOrElse { ex ->
            val reason = when (ex) {
                is HttpStatusException -> "HTTP ${ex.statusCode} from $url"
                else                   -> ex.message ?: "Unknown network error"
            }
            throw JobScrapingException("Failed to fetch job posting: $reason", ex)
        }

        // Try targeted selectors first
        for ((label, selector) in SELECTOR_CHAIN) {
            val text = doc.select(selector).text().trim()
            if (text.isNotBlank()) {
                log.debug("Matched selector '{}' ({})", selector, label)
                return normalise(text)
            }
        }

        // Fallback – strip chrome elements and use the whole body
        log.warn("No targeted selector matched for {} – falling back to full body", url)
        doc.select("script, style, nav, header, footer, aside, iframe").remove()
        val bodyText = doc.body()?.text()?.trim().orEmpty()

        if (bodyText.isBlank()) {
            throw JobScrapingException(
                "Could not extract any text from the job posting at: $url. " +
                "The page may require JavaScript rendering."
            )
        }

        return normalise(bodyText)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Collapse excessive whitespace / blank lines without losing structure. */
    private fun normalise(raw: String): String =
        raw
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(\\r?\\n){3,}"), "\n\n")
            .trim()
}

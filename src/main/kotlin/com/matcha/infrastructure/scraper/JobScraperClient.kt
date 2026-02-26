package com.matcha.infrastructure.scraper

import com.matcha.exception.JobScrapingException
import org.jsoup.Connection
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
 * Includes comprehensive browser-like headers (User-Agent, Accept, Referer, etc.)
 * to avoid 403 rejections from modern job boards like Coupang, LinkedIn, Indeed, etc.
 */
@Component
class JobScraperClient {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val USER_AGENTS =
            listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            )
        private val COOKIE_JAR =
            listOf(
                // Add more real or browser-extracted cookies for target sites if needed
                "_ga=GA1.2.123456789.1234567890; _gid=GA1.2.987654321.0987654321",
                "cf_clearance=your_clearance_cookie_here; _ga=GA1.2.123456789.1234567890; _gid=GA1.2.987654321.0987654321",
            )
        private const val TIMEOUT_MS = 30_000

        /**
         * Ordered list of (label, cssSelector) pairs tried top-down.
         * The first non-blank result wins.
         */
        private val SELECTOR_CHAIN =
            listOf(
                "LinkedIn" to "div.description__text",
                "LinkedIn" to "div.show-more-less-html__markup",
                "Indeed" to "div#jobDescriptionText",
                "Indeed" to "div.jobsearch-JobComponent-description",
                "ATS/Generic" to "div#content",
                "ATS/Generic" to "article",
                "ATS/Generic" to "main",
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
        var lastException: Exception? = null
        val maxAttempts = 5
        var attempt = 0
        while (attempt < maxAttempts) {
            val userAgent = USER_AGENTS.random()
            val cookie = COOKIE_JAR.random()
            try {
                val conn: Connection =
                    Jsoup
                        .connect(url)
                        .userAgent(userAgent)
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .referrer("https://www.google.com/")
                        .header("Cookie", cookie)
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                        ).header("Accept-Encoding", "gzip, deflate, br")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Cache-Control", "max-age=0")
                        .header("Pragma", "no-cache")
                        .header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"120\", \"Chromium\";v=\"120\"")
                        .header("Sec-Ch-Ua-Mobile", "?0")
                        .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Sec-Fetch-User", "?1")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("DNT", "1")
                        .header("Connection", "keep-alive")
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)

                val doc: Document = conn.get()

                // Detect Cloudflare or block page
                val bodyText = doc.body()?.text()?.lowercase() ?: ""
                if (bodyText.contains("cloudflare ray id") || bodyText.contains("please enable cookies") || bodyText.contains("blocked")) {
                    log.warn(
                        "Block page detected (Cloudflare or similar) on attempt {} for {}. Retrying with new headers/cookies...",
                        attempt + 1,
                        url,
                    )
                    Thread.sleep(1000L * (attempt + 1))
                    attempt++
                    continue
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
                val fallbackBodyText =
                    doc
                        .body()
                        ?.text()
                        ?.trim()
                        .orEmpty()

                if (fallbackBodyText.isBlank()) {
                    throw JobScrapingException(
                        "Could not extract any text from the job posting at: $url. " +
                            "The page may require JavaScript rendering.",
                    )
                }

                return normalise(fallbackBodyText)
            } catch (ex: Exception) {
                lastException = ex
                if (ex is HttpStatusException && ex.statusCode == 403) {
                    log.warn("HTTP 403 on attempt {} for {}. Retrying...", attempt + 1, url)
                    Thread.sleep(500L * (attempt + 1))
                    attempt++
                    continue
                } else {
                    break
                }
            }
        }
        throw JobScrapingException("Failed to fetch job posting after $maxAttempts attempts: ${lastException?.message}", lastException)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Collapse excessive whitespace / blank lines without losing structure. */
    private fun normalise(raw: String): String =
        raw
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(\\r?\\n){3,}"), "\n\n")
            .trim()
}

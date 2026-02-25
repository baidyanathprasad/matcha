package com.matcha.infrastructure.mail

import com.matcha.config.AppProperties
import com.matcha.domain.model.MatchResult
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * Infrastructure adapter responsible for dispatching HTML match-summary emails
 * via Spring Mail / Gmail SMTP.
 *
 * The threshold check is intentionally performed here rather than in the service
 * layer so that the orchestrator simply calls [sendIfEligible] and receives a
 * boolean – the policy detail stays encapsulated in this adapter.
 */
@Component
class MailNotificationClient(
    private val mailSender: JavaMailSender,
    private val props: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Send a notification email if [result.score] exceeds the configured threshold.
     *
     * @param result  The AI-generated match assessment.
     * @param jobUrl  Original job posting URL (linked in the email body).
     * @return        `true` if an email was dispatched, `false` otherwise.
     */
    fun sendIfEligible(result: MatchResult, jobUrl: String): Boolean {
        val threshold = props.match.scoreThreshold

        if (result.score <= threshold) {
            log.info("Score {} ≤ threshold {} – no email sent.", result.score, threshold)
            return false
        }

        log.info("Score {} > threshold {} – dispatching match email.", result.score, threshold)
        return dispatch(result, jobUrl)
    }

    // ── private ───────────────────────────────────────────────────────────────

    private fun dispatch(result: MatchResult, jobUrl: String): Boolean {
        val subject = "${props.email.subjectPrefix} Match Score ${result.score}/100 – Action Recommended"

        return runCatching {
            val message = mailSender.createMimeMessage()
            MimeMessageHelper(message, true, "UTF-8").apply {
                setFrom(props.email.from)
                setTo(props.email.to)
                setSubject(subject)
                setText(buildHtml(result, jobUrl), /* html = */ true)
            }
            mailSender.send(message)
            log.info("Match email sent to '{}' with subject '{}'", props.email.to, subject)
        }
        .map { true }
        .getOrElse { ex ->
            // Email failure is non-fatal – log the error but don't blow up the response
            log.error("Failed to send match email to '{}': {}", props.email.to, ex.message, ex)
            false
        }
    }

    /**
     * Renders the match summary as an inline-CSS HTML email body.
     * Inline CSS is used for maximum Gmail / Outlook compatibility.
     */
    private fun buildHtml(result: MatchResult, jobUrl: String): String {
        val scoreColour = when {
            result.score >= 85 -> "#27ae60"   // green
            result.score >= 75 -> "#e67e22"   // orange
            else               -> "#e74c3c"   // red
        }

        val matchedHtml = result.matchedSkills
            .joinToString("") { "<li style='margin-bottom:4px;'>$it</li>" }
            .ifBlank { "<li><em>None identified</em></li>" }

        val gapsHtml = result.gaps
            .joinToString("") { "<li style='margin-bottom:4px;'>$it</li>" }
            .ifBlank { "<li><em>No significant gaps found</em></li>" }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#333;">

              <table width="100%" cellpadding="0" cellspacing="0"
                     style="background:#f4f6f8;padding:32px 0;">
                <tr><td align="center">
                  <table width="620" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:8px;overflow:hidden;
                                box-shadow:0 2px 8px rgba(0,0,0,.08);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#2c3e50;padding:24px 32px;">
                        <h1 style="margin:0;color:#fff;font-size:22px;">
                          🎯 Resume / Job Match Report
                        </h1>
                      </td>
                    </tr>

                    <!-- Score -->
                    <tr>
                      <td style="padding:28px 32px 0;">
                        <table width="100%" cellpadding="0" cellspacing="0"
                               style="border:1px solid #e0e0e0;border-radius:6px;overflow:hidden;">
                          <tr>
                            <td style="padding:14px 18px;background:#f9f9f9;
                                        font-weight:bold;width:40%;">Match Score</td>
                            <td style="padding:14px 18px;font-size:32px;
                                        font-weight:bold;color:$scoreColour;">
                              ${result.score} / 100
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 18px;background:#f9f9f9;
                                        font-weight:bold;vertical-align:top;">Job Posting</td>
                            <td style="padding:14px 18px;">
                              <a href="$jobUrl" style="color:#2980b9;
                                                       word-break:break-all;">$jobUrl</a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>

                    <!-- Matched Skills -->
                    <tr>
                      <td style="padding:24px 32px 0;">
                        <h2 style="font-size:16px;color:#27ae60;margin:0 0 10px;">
                          ✅ Matched Skills
                        </h2>
                        <ul style="margin:0;padding-left:20px;line-height:1.8;">
                          $matchedHtml
                        </ul>
                      </td>
                    </tr>

                    <!-- Gaps -->
                    <tr>
                      <td style="padding:24px 32px 0;">
                        <h2 style="font-size:16px;color:#e74c3c;margin:0 0 10px;">
                          ⚠️ Skill Gaps
                        </h2>
                        <ul style="margin:0;padding-left:20px;line-height:1.8;">
                          $gapsHtml
                        </ul>
                      </td>
                    </tr>

                    <!-- AI Rationale -->
                    <tr>
                      <td style="padding:24px 32px 0;">
                        <h2 style="font-size:16px;color:#2c3e50;margin:0 0 10px;">
                          💬 AI Rationale
                        </h2>
                        <p style="margin:0;padding:14px 18px;background:#f0f7ff;
                                   border-left:4px solid #2980b9;
                                   border-radius:4px;line-height:1.7;">
                          ${result.matchReason}
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="padding:28px 32px;border-top:1px solid #eee;
                                  margin-top:28px;font-size:12px;color:#999;">
                        Generated by Resume Matcher · Powered by llama3.2 via Ollama
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>

            </body>
            </html>
        """.trimIndent()
    }
}

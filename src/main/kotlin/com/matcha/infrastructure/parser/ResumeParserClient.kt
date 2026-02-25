package com.matcha.infrastructure.parser

import com.matcha.exception.ResumeParsingException
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

/**
 * Infrastructure component responsible for extracting plain text from
 * an uploaded résumé file using Apache Tika.
 *
 * Tika auto-detects the file format (PDF, DOCX, ODT, etc.) – no changes
 * needed here if new formats are required.
 *
 * Named "Client" to signal that this is an infrastructure adapter –
 * it wraps an external library (Tika) rather than containing business logic.
 */
@Component
class ResumeParserClient(private val tika: Tika) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Extract and return the plain-text content of the uploaded file.
     *
     * @param file Spring multipart upload (PDF expected).
     * @return Trimmed, non-blank plain text.
     * @throws ResumeParsingException if the file is empty or yields no text.
     */
    fun extractText(file: MultipartFile): String {
        if (file.isEmpty) {
            throw ResumeParsingException("Uploaded résumé file is empty.")
        }

        val filename  = file.originalFilename ?: "unknown"
        val mediaType = tika.detect(file.bytes)
        log.info("Parsing '{}' detected media type: '{}'", filename, mediaType)

        val text = runCatching {
            file.inputStream.use { stream -> tika.parseToString(stream) }
        }.getOrElse { ex ->
            throw ResumeParsingException(
                "Failed to parse '$filename': ${ex.message}", ex
            )
        }.trim()

        if (text.isBlank()) {
            throw ResumeParsingException(
                "No text could be extracted from '$filename'. " +
                "Ensure the PDF is not a scanned image without an OCR layer."
            )
        }

        log.debug("Extracted {} characters from '{}'", text.length, filename)
        return text
    }
}

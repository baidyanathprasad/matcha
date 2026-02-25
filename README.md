# Resume Matcher

A local Mac-based AI-powered résumé ↔ job description matcher built with
**Spring Boot 3.4**, **Spring AI**, **Ollama (llama3.2)**, **Apache Tika**, and **Jsoup**.

---

## Project Structure

```
src/main/kotlin/com/matcha/
├── Matcha.kt                         ← Application entry point
├── config/
│   ├── AppConfig.kt                         ← Spring @Bean definitions (Tika)
│   └── AppProperties.kt                     ← Type-safe @ConfigurationProperties
├── domain/
│   └── model/
│       └── MatchResult.kt                   ← Core domain model (AI output)
├── exception/
│   └── Exceptions.kt                        ← Typed application exceptions
├── infrastructure/
│   ├── ai/
│   │   └── AiMatchingClient.kt              ← Ollama / Spring AI adapter
│   ├── mail/
│   │   └── MailNotificationClient.kt        ← Spring Mail / Gmail SMTP adapter
│   ├── parser/
│   │   └── ResumeParserClient.kt            ← Apache Tika PDF extraction
│   └── scraper/
│       └── JobScraperClient.kt              ← Jsoup web scraper
├── service/
│   ├── MatchOrchestrationService.kt         ← Business service interface
│   └── impl/
│       └── MatchOrchestrationServiceImpl.kt ← Pipeline orchestration
└── web/
    ├── advice/
    │   └── GlobalExceptionHandler.kt        ← Centralised error mapping
    ├── controller/
    │   └── MatchController.kt               ← REST endpoints
    ├── request/
    │   └── MatchRequest.kt                  ← Inbound DTO
    └── response/
        ├── ErrorResponse.kt                 ← Error envelope
        └── MatchResponse.kt                 ← Success response DTO
```

---

## Prerequisites

| Tool    | Version  |
|---------|----------|
| JDK     | 21       |
| Ollama  | latest   |
| Gradle  | (wrapper)|

---

## Setup

### 1. Pull the Ollama model

```bash
ollama pull llama3.2
```

### 2. Configure environment variables

```bash
export GMAIL_USERNAME=you@gmail.com
export GMAIL_APP_PASSWORD=your-16-char-app-password   # Gmail App Password, NOT login pw
export NOTIFY_EMAIL=recipient@example.com
```

> **Gmail App Password:** Go to Google Account → Security → 2-Step Verification → App Passwords.

### 3. Run the application

```bash
./gradlew bootRun
```

The server starts on **http://localhost:8080**.

---

## API Reference

### POST `/api/v1/matches`

Upload a PDF résumé and provide a job posting URL.

```bash
curl -X POST http://localhost:8080/api/v1/matches \
     -F "resume=@/path/to/my-resume.pdf"          \
     -F "jobUrl=https://www.linkedin.com/jobs/view/123456789"
```

**Response (201 Created):**

```json
{
  "score": 82,
  "matchedSkills": ["Kotlin", "Spring Boot", "REST APIs", "PostgreSQL"],
  "gaps": ["Kubernetes", "Terraform", "3+ years Kafka"],
  "matchReason": "Strong backend match. Gaps mainly in DevOps tooling.",
  "emailSent": true,
  "jobUrl": "https://www.linkedin.com/jobs/view/123456789"
}
```

### GET `/api/v1/health`

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","service":"matcha","version":"1.0.0"}
```

---

## Configuration

All knobs live in `src/main/resources/application.yml`:

| Key                           | Default    | Description                       |
|-------------------------------|------------|-----------------------------------|
| `app.match.score-threshold`   | `75`       | Minimum score to trigger email    |
| `app.match.resume-char-limit` | `4000`     | Max chars sent to LLM from résumé |
| `app.match.jd-char-limit`     | `4000`     | Max chars sent to LLM from JD     |
| `spring.ai.ollama.chat.model` | `llama3.2` | Ollama model name                 |

---

## Running Tests

```bash
./gradlew test
```

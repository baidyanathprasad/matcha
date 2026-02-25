# Matcha

A local Mac-based AI-powered résumé ↔ job description matcher built with
**Spring Boot 3.4**, **Spring AI**, **Ollama (llama3.2)**, **Apache Tika**, and **Jsoup**.

Features a beautiful, responsive single-page UI with gradient design, real-time analysis, and smart resume caching.

---

## 🎯 Quick Start

### Prerequisites

| Tool   | Version   |
|--------|-----------|
| JDK    | 21        |
| Ollama | latest    |
| Gradle | (wrapper) |

### 1. Install & Start Ollama

```bash
# macOS (using Homebrew)
brew install ollama

# Start the Ollama service
ollama serve
```

Keep the Ollama terminal open. In another terminal, pull the model:

```bash
ollama pull llama3.2
```

### 2. Configure Environment Variables

```bash
export GMAIL_USERNAME=your-email@gmail.com
export GMAIL_APP_PASSWORD=your-16-char-app-password
export NOTIFY_EMAIL=recipient@example.com
```

> **Note:** `GMAIL_APP_PASSWORD` is from Google Account → Security → 2-Step Verification → App Passwords (NOT your login password).

### 3. Run the Application

```bash
./gradlew bootRun
```

The app starts on **http://localhost:8080**

### 4. Open the UI

Open your browser and navigate to:

```
http://localhost:8080
```

You'll see the Matcha UI with:
- 📄 Resume upload (drag & drop or click)
- 🔗 Job URL input
- 🎯 Beautiful results page with color-coded scores

---

## 🏗️ Architecture

### Backend (Spring Boot)

```
src/main/kotlin/com/matcha/
├── MatchaApp.kt                      ← Entry point
├── config/
│   ├── AppConfig.kt                  ← Spring beans & CORS config
│   └── AppProperties.kt              ← Type-safe configuration
├── domain/
│   └── model/MatchResult.kt          ← Domain model
├── exception/
│   └── Exceptions.kt                 ← Custom exceptions
├── infrastructure/
│   ├── ai/AiMatchingClient.kt        ← Ollama / Spring AI adapter
│   ├── mail/MailNotificationClient.kt ← Email notifications
│   ├── parser/ResumeParserClient.kt  ← PDF extraction (Tika)
│   ├── scraper/JobScraperClient.kt   ← Web scraping (Jsoup)
│   └── health/
│       ├── OllamaHealthCheck.kt      ← Health endpoint
│       └── OllamaStartupValidator.kt ← Startup checks
├── service/
│   ├── MatchOrchestrationService.kt  ← Business logic interface
│   └── impl/MatchOrchestrationServiceImpl.kt ← Pipeline
└── web/
    ├── advice/GlobalExceptionHandler.kt ← Error handling
    ├── controller/
    │   ├── MatchController.kt        ← REST API endpoints
    │   └── StaticController.kt       ← Static file serving
    ├── request/MatchRequest.kt
    └── response/
        ├── ErrorResponse.kt
        └── MatchResponse.kt
```

### Frontend (Vanilla HTML/CSS/JS)

```
src/main/resources/
└── static/
    └── index.html                    ← Single-page app
        ├── Home page (resume upload)
        ├── Loading page (spinner)
        ├── Results page (score + skills)
        └── Error page (with retry)
```

**Features:**
- ✨ Gradient background with glassmorphism
- 🎨 Modern, responsive design (mobile-first)
- 💾 Smart resume caching (sessionStorage)
- 🚀 Fast, lightweight (no dependencies)
- ♿ Accessible (keyboard nav, ARIA labels)

---

## 🌐 API Reference

### POST `/api/v1/matches`

Upload a PDF résumé and job URL for analysis.

**Request:**

```bash
curl -X POST http://localhost:8080/api/v1/matches \
     -F "resume=@resume.pdf" \
     -F "jobUrl=https://www.linkedin.com/jobs/view/123456"
```

**Response (201 Created):**

```json
{
  "score": 83,
  "matchedSkills": [
    "Machine Learning",
    "Software Development",
    "Cloud Computing"
  ],
  "gaps": [
    "Experience mentoring teams",
    "5+ years in role"
  ],
  "matchReason": "Strong technical fit with minor leadership gaps.",
  "emailSent": true,
  "jobUrl": "https://www.linkedin.com/jobs/view/123456"
}
```

### GET `/api/v1/health`

Health check endpoint.

```bash
curl http://localhost:8080/api/v1/health
```

Response:

```json
{
  "status": "UP",
  "service": "matcha",
  "version": "1.0.0"
}
```

### GET `/api/v1/health/ollama`

Check Ollama service availability.

```bash
curl http://localhost:8080/api/v1/health/ollama
```

Response (when up):

```json
{
  "status": "UP",
  "service": "ollama",
  "url": "http://localhost:11434/api/tags",
  "responseCode": 200
}
```

---

## ⚙️ Configuration

All settings in `src/main/resources/application.yml`:

| Key                                         | Default                  | Description                          |
|---------------------------------------------|--------------------------|--------------------------------------|
| `app.match.score-threshold`                 | `75`                     | Min score to send notification email |
| `app.match.resume-char-limit`               | `4000`                   | Max chars from résumé sent to LLM    |
| `app.match.jd-char-limit`                   | `4000`                   | Max chars from JD sent to LLM        |
| `spring.ai.ollama.base-url`                 | `http://localhost:11434` | Ollama service URL                   |
| `spring.ai.ollama.chat.model`               | `llama3.2`               | Model name to use                    |
| `spring.ai.ollama.chat.options.temperature` | `0.2`                    | Deterministic output                 |
| `spring.mail.host`                          | `smtp.gmail.com`         | Email server                         |
| `spring.mail.port`                          | `587`                    | SMTP port                            |

---

## 🎨 UI Features

### Resume Caching

Upload a resume once, then analyze multiple job postings:

1. **First Analysis:** Upload resume + enter job URL → Analyze
2. **Second Analysis:** Click "Try Another" → Only change job URL → Analyze
3. Resume stays in memory (sessionStorage) until page reload

### Color-Coded Scores

- 🟢 **Green (75-100):** Excellent match → Email notification sent
- 🟡 **Orange (50-74):** Moderate match → No email
- 🔴 **Red (0-49):** Poor match → No email

### Smart Error Messages

When something fails, clear error messages guide you:

- "Ollama service is not running. Please start it with: `ollama serve`"
- "Cannot resolve Ollama host 'localhost'. If Ollama is running on a different machine, update spring.ai.ollama.base-url"
- Full troubleshooting steps included

---

## 🚀 Running Tests

```bash
./gradlew test
```

---

## 📚 Documentation

- **UI Guide:** See `UI_GUIDE.md` for customization, styling, and advanced features
- **Ollama Setup:** See `OLLAMA_SETUP.md` for Ollama installation & troubleshooting
- **Git Ignore:** `.gitignore` is configured for Java/Kotlin/Gradle projects

---

## 🐛 Troubleshooting

### "Ollama call failed: I/O error..."

**Cause:** Ollama service not running  
**Fix:** Start Ollama in a terminal:

```bash
ollama serve
```

Then verify it's accessible:

```bash
curl http://localhost:11434/api/tags
```

### "Could not deserialize AI response..."

**Cause:** Model returned invalid JSON  
**Fix:** Verify model is running:

```bash
ollama list
```

Pull the model if missing:

```bash
ollama pull llama3.2
```

### Upload not accepting PDF

**Cause:** File size > 20MB or invalid PDF  
**Fix:** Check file size and format, then retry

### Styling looks broken

**Cause:** Browser cache  
**Fix:** Hard refresh (Cmd+Shift+R on Mac, Ctrl+Shift+R on Windows)

---

## 📦 Dependencies

### Backend

- **Spring Boot 3.4** - Web framework
- **Spring AI 1.0.0-M6** - LLM integration
- **Ollama** - Local LLM inference
- **Apache Tika 2.9.2** - PDF extraction
- **Jsoup 1.18.1** - Web scraping
- **Jakarta Mail** - Email notifications

### Frontend

- Vanilla HTML5
- CSS3 (no framework, ~400 lines)
- JavaScript ES6+ (no dependencies)

---

## 🎯 Next Steps

1. ✅ Start Ollama: `ollama serve`
2. ✅ Pull model: `ollama pull llama3.2`
3. ✅ Set env vars: `export GMAIL_USERNAME=...`
4. ✅ Run app: `./gradlew bootRun`
5. ✅ Open UI: http://localhost:8080

---

## 📝 License

MIT License - feel free to use for personal projects

---

**Last Updated:** February 25, 2026  
**Version:** 1.0.0

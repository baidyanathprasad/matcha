// Matcha frontend behavior (extracted from index.html)
// Handles file upload caching, AJAX submission, and page state transitions

// ── State Management ───────────────────────────────────────────────────
const state = {
    resumeFile: null,
    showPage: (pageName) => {
        document.getElementById('homePage').classList.toggle('hidden', pageName !== 'home');
        document.getElementById('loadingPage').classList.toggle('hidden', pageName !== 'loading');
        document.getElementById('resultsPage').classList.toggle('hidden', pageName !== 'results');
        document.getElementById('errorPage').classList.toggle('hidden', pageName !== 'error');
    }
};

// ── DOM Elements ───────────────────────────────────────────────────────
const elements = {
    form: document.getElementById('matchForm'),
    resumeInput: document.getElementById('resumeInput'),
    fileInputWrapper: document.getElementById('fileInputWrapper'),
    fileName: document.getElementById('fileName'),
    jobUrl: document.getElementById('jobUrl'),
    submitBtn: document.querySelector('.btn-primary'),
    submitSpinner: document.getElementById('submitSpinner'),
    submitText: document.getElementById('submitText'),
    scoreCircle: document.getElementById('scoreCircle'),
    scoreLabel: document.getElementById('scoreLabel'),
    matchReason: document.getElementById('matchReason'),
    jobUrlLink: document.getElementById('jobUrlLink'),
    emailStatus: document.getElementById('emailStatus'),
    matchedSkills: document.getElementById('matchedSkills'),
    gapsList: document.getElementById('gapsList'),
    tryAnotherBtn: document.getElementById('tryAnotherBtn'),
    errorMessage: document.getElementById('errorMessage'),
    retryBtn: document.getElementById('retryBtn')
};

// ── File Input Handler ─────────────────────────────────────────────────
elements.resumeInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
        state.resumeFile = file;
        elements.fileName.textContent = `✓ ${file.name}`;
        elements.fileName.classList.remove('error');
    }
});

// Drag and drop
elements.fileInputWrapper.addEventListener('dragover', (e) => {
    e.preventDefault();
    elements.fileInputWrapper.classList.add('drag-over');
});

elements.fileInputWrapper.addEventListener('dragleave', () => {
    elements.fileInputWrapper.classList.remove('drag-over');
});

elements.fileInputWrapper.addEventListener('drop', (e) => {
    e.preventDefault();
    elements.fileInputWrapper.classList.remove('drag-over');
    const files = e.dataTransfer.files;
    if (files.length > 0) {
        elements.resumeInput.files = files;
        const event = new Event('change', { bubbles: true });
        elements.resumeInput.dispatchEvent(event);
    }
});

// ── Form Submission (AJAX) ──────────────────────────────────────────────
elements.form.addEventListener('submit', async (e) => {
    e.preventDefault();

    if (!state.resumeFile) {
        elements.fileName.textContent = '✗ Please select a resume file';
        elements.fileName.classList.add('error');
        return;
    }

    const jobUrl = elements.jobUrl.value.trim();
    if (!jobUrl) {
        alert('Please enter a job URL');
        return;
    }

    // Show loading state
    elements.submitBtn.disabled = true;
    elements.submitSpinner.style.display = 'inline-block';
    elements.submitText.textContent = 'Analyzing...';
    state.showPage('loading');

    try {
        const formData = new FormData();
        formData.append('resume', state.resumeFile);
        formData.append('jobUrl', jobUrl);

        const response = await fetch('/api/v1/matches', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            throw new Error(errorData?.message || `HTTP ${response.status}`);
        }

        const data = await response.json();
        displayResults(data);
    } catch (error) {
        displayError(error.message);
    } finally {
        elements.submitBtn.disabled = false;
        elements.submitSpinner.style.display = 'none';
        elements.submitText.textContent = 'Analyze Match';
    }
});

// ── Display Results ────────────────────────────────────────────────────
function displayResults(data) {
    const score = data.score;
    elements.scoreCircle.textContent = `${score}%`;

    if (score >= 75) {
        elements.scoreCircle.classList.remove('low', 'medium');
    } else if (score >= 50) {
        elements.scoreCircle.classList.remove('low');
        elements.scoreCircle.classList.add('medium');
    } else {
        elements.scoreCircle.classList.add('low');
        elements.scoreCircle.classList.remove('medium');
    }

    elements.matchReason.textContent = data.matchReason;
    elements.jobUrlLink.href = data.jobUrl;
    elements.jobUrlLink.textContent = new URL(data.jobUrl).hostname;

    if (data.emailSent) {
        elements.emailStatus.className = 'email-status sent';
        elements.emailStatus.innerHTML = '✓ <strong>Notification sent!</strong> You\'ll receive an email shortly.';
    } else {
        elements.emailStatus.className = 'email-status not-sent';
        elements.emailStatus.innerHTML = '⚠ <strong>Below threshold.</strong> Email notification was not sent.';
    }

    elements.matchedSkills.innerHTML = (data.matchedSkills || [])
        .map(skill => `<div class="skill-badge match">${skill}</div>`)
        .join('');

    elements.gapsList.innerHTML = (data.gaps || [])
        .map(gap => `<div class="skill-badge gap">${gap}</div>`)
        .join('');

    state.showPage('results');
}

// ── Display Error ─────────────────────────────────────────────────────
function displayError(errorMessage) {
    elements.errorMessage.textContent = errorMessage;
    state.showPage('error');
}

// ── Action Buttons ────────────────────────────────────────────────────
elements.tryAnotherBtn.addEventListener('click', () => {
    // Keep the uploaded resume cached in memory so the user doesn't need to re-upload.
    // Only clear the job URL and return to the home page.
    elements.jobUrl.value = '';
    elements.fileName.textContent = state.resumeFile ? `✓ ${state.resumeFile.name}` : '';
    state.showPage('home');
});

elements.retryBtn.addEventListener('click', () => {
    elements.fileName.textContent = state.resumeFile ? `✓ ${state.resumeFile.name}` : '';
    state.showPage('home');
});

// Initialize
state.showPage('home');


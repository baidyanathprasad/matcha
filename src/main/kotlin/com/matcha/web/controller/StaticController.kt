package com.matcha.web.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Controller to serve static HTML pages and handle client-side routing.
 */
@Controller
class StaticController {
    /**
     * Serve the main index.html at the root path.
     * Spring Boot automatically serves static files from src/main/resources/static/
     */
    @GetMapping("/")
    fun index() = "forward:/index.html"
}

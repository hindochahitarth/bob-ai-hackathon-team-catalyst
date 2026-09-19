package com.teamcatalyst.supplychain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * GlobalExceptionHandler — catches unhandled controller exceptions and
 * renders a branded error page instead of the Spring default whitepage.
 *
 * IMPORTANT: Do NOT handle NoResourceFoundException or WebExchange here.
 * Static resources (/css, /js, /images) are served by Spring's own
 * ResourceHttpRequestHandler and must NOT be intercepted — otherwise
 * CSS/JS files return a Thymeleaf "error" view instead of their content.
 *
 * spring.mvc.throw-exception-if-no-handler-found=true (in application.properties)
 * ensures that unmapped *controller* URLs throw NoHandlerFoundException,
 * which we catch below for a friendly 404 page.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — a browser navigated to a URL that has no controller mapping.
     * Triggered by spring.mvc.throw-exception-if-no-handler-found=true.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handle404(NoHandlerFoundException ex, Model model) {
        log.warn("404 Not Found: {}", ex.getRequestURL());
        model.addAttribute("status",  404);
        model.addAttribute("title",   "Page Not Found");
        model.addAttribute("message", "The page you requested does not exist on this platform.");
        model.addAttribute("detail",  "URL: " + ex.getRequestURL());
        return "error";
    }

    /**
     * 500 — an unexpected exception escaped a controller.
     * Excluded: TemplateInputException (would cause infinite render loop).
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handle500(Exception ex, Model model) {
        // Guard: if this is a Thymeleaf template exception, don't recurse into error.html
        String exClass = ex.getClass().getName();
        if (exClass.contains("TemplateInput") || exClass.contains("TemplateProcessing")) {
            log.error("Template error (not re-rendering error page): {}", ex.getMessage());
            throw new RuntimeException(ex); // let Spring's default handler deal with it
        }
        log.error("500 Internal Server Error [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        model.addAttribute("status",  500);
        model.addAttribute("title",   "Internal Server Error");
        model.addAttribute("message", "An unexpected error occurred. The operations team has been notified.");
        model.addAttribute("detail",  ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return "error";
    }
}

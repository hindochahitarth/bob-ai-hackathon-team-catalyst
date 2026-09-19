package com.teamcatalyst.supplychain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * GlobalExceptionHandler — catches unhandled controller exceptions and
 * renders a branded error page instead of the Spring default whitepage.
 *
 * Explicitly suppressed (not rendered as error pages):
 *  - NoResourceFoundException  — browser requests favicon.ico / static files that
 *    don't exist; these are normal and must NOT trigger a Thymeleaf render.
 *  - IOException (connection abort) — client closes tab/browser mid-SSE stream;
 *    this is expected and harmless.
 *
 * Only NoHandlerFoundException (unmapped controller URL) produces a 404 page.
 * All other unexpected exceptions produce a 500 page.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Silently swallow favicon / missing static resource 404s.
     * Browsers always request /favicon.ico — if we don't have one, just return 404
     * without logging an error or trying to render a Thymeleaf page.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleMissingResource(NoResourceFoundException ex) {
        // Intentionally empty — no logging, no template rendering.
        // Returning void with @ResponseStatus sends a clean 404 with empty body.
    }

    /**
     * Silently swallow IOException caused by the client aborting the connection
     * (e.g. browser tab closed while SSE stream is open). These are not errors.
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.OK)
    public void handleClientAbort(IOException ex) {
        // Only suppress genuine client-abort messages; log anything unexpected at DEBUG.
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("aborted") || msg.contains("reset") || msg.contains("Broken pipe"))) {
            log.debug("Client disconnected (SSE or response): {}", msg);
        } else {
            log.warn("IOException (possibly client disconnect): {}", msg);
        }
    }

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
     * Guard: TemplateProcessingException would cause an infinite render loop.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handle500(Exception ex, Model model) {
        String exClass = ex.getClass().getName();
        // Don't recurse: Thymeleaf errors can't be rendered via Thymeleaf
        if (exClass.contains("TemplateInput") || exClass.contains("TemplateProcessing")) {
            log.error("Template render error (suppressing to avoid loop): {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
        log.error("500 Internal Server Error [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        model.addAttribute("status",  500);
        model.addAttribute("title",   "Internal Server Error");
        model.addAttribute("message", "An unexpected error occurred. The operations team has been notified.");
        model.addAttribute("detail",  ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return "error";
    }
}

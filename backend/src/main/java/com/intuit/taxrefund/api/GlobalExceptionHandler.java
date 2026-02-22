package com.intuit.taxrefund.api;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        // Expected client error -> WARN (no stack trace)
        log.warn("bad_request path={} method={} msg={}",
            req.getRequestURI(), req.getMethod(), safeMsg(ex.getMessage()));

        return new ApiError(
            Instant.now(),
            400,
            "Bad Request",
            ex.getMessage(),
            req.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult()
            .getAllErrors()
            .stream()
            .findFirst()
            .map(e -> e.getDefaultMessage())
            .orElse("Validation error");

        // Expected client error -> WARN (no stack trace)
        log.warn("validation_failed path={} method={} msg={}",
            req.getRequestURI(), req.getMethod(), safeMsg(msg));

        return new ApiError(
            Instant.now(),
            400,
            "Bad Request",
            msg,
            req.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Invalid parameter: " + ex.getName();

        log.warn("type_mismatch path={} method={} param={} value={}",
            req.getRequestURI(), req.getMethod(), ex.getName(),
            ex.getValue() == null ? "null" : String.valueOf(ex.getValue()));

        return new ApiError(
            Instant.now(),
            400,
            "Bad Request",
            msg,
            req.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError serverError(Exception ex, HttpServletRequest req) {
        // Unexpected server error -> ERROR with stack trace
        log.error("server_error path={} method={} errType={} msg={}",
            req.getRequestURI(), req.getMethod(), ex.getClass().getSimpleName(), safeMsg(ex.getMessage()), ex);

        // For prod we might return a generic message; for demo keep ex.getMessage()
        return new ApiError(
            Instant.now(),
            500,
            "Internal Server Error",
            ex.getMessage(),
            req.getRequestURI()
        );
    }

    private static String safeMsg(String msg) {
        if (msg == null) return "";
        // very lightweight redaction (optional). Add patterns later if needed.
        if (msg.toLowerCase().contains("token")) return "[redacted]";
        return msg;
    }
}
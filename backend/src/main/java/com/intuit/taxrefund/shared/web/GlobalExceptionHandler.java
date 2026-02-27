package com.intuit.taxrefund.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("bad_request path={} method={} msg={}",
            req.getRequestURI(), req.getMethod(), safeMsg(ex.getMessage()));

        return new ApiError(
            Instant.now(),
            400,
            "Bad Request",
            ex.getMessage(),
            req.getRequestURI(),
            null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            // Keep first error per field to avoid noisy duplicates
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }

        String msg = fieldErrors.isEmpty()
            ? "Validation error"
            : "Validation failed for " + fieldErrors.size() + " field(s)";

        log.warn("validation_failed path={} method={} fieldErrors={}",
            req.getRequestURI(), req.getMethod(), fieldErrors);

        return new ApiError(
            Instant.now(),
            400,
            "Bad Request",
            msg,
            req.getRequestURI(),
            fieldErrors
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
            req.getRequestURI(),
            Map.of(ex.getName(), "Invalid value")
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError serverError(Exception ex, HttpServletRequest req) {
        log.error("server_error path={} method={} errType={} msg={}",
            req.getRequestURI(), req.getMethod(), ex.getClass().getSimpleName(), safeMsg(ex.getMessage()), ex);

        return new ApiError(
            Instant.now(),
            500,
            "Internal Server Error",
            ex.getMessage(),
            req.getRequestURI(),
            null
        );
    }

    private static String safeMsg(String msg) {
        if (msg == null) return "";
        if (msg.toLowerCase().contains("token")) return "[redacted]";
        return msg;
    }
}
package com.intuit.taxrefundstatus.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError badRequest(IllegalAccessException ex, HttpServletRequest req) {
        return new ApiError(Instant.now(), 400, "Bad Request", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiError validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return new ApiError(
                Instant.now(),
                400,
                "Bad request",
                ex.getBindingResult()
                        .getAllErrors()
                        .stream()
                        .findFirst()
                        .map(e -> e.getDefaultMessage())
                        .orElse("Validation error"),
                req.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ApiError serverError(Exception ex, HttpServletRequest req) {
        return new ApiError(
                Instant.now(),
                500,
                "Internal server error",
                ex.getMessage(),
                req.getRequestURI()
        );
    }
}

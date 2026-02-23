package com.intuit.taxrefund.shared.web;

import java.time.Instant;

public record ApiError(
   Instant timestamp,
   int status,
   String error,
   String message,
   String path
) {}

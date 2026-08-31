package com.systar.server.common;

import com.systar.common.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global exception handler that converts unhandled exceptions into
 * consistent {@link Result} JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequest(IllegalArgumentException ex) {
        return Result.error(Result.CODE_BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleConflict(IllegalStateException ex) {
        return Result.error(Result.CODE_CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Result<Void> handleResponseStatusException(ResponseStatusException ex) {
        return Result.error(ex.getStatusCode().value(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Result.error(Result.CODE_INTERNAL_ERROR, "Internal server error");
    }
}

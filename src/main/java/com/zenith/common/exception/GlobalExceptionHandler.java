package com.zenith.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler – returns RFC 7807 ProblemDetail responses with correlation IDs.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(SimulatedFailureException.class)
    public ResponseEntity<ProblemDetail> handleSimulatedFailure(SimulatedFailureException ex) {
        log.error("Handling simulated failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "FAILURE_SIMULATION", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a
                ));
        return buildResponseWithFields(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed. Check the 'fields' property for details.", fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<ProblemDetail> buildResponse(HttpStatus status, String code, String detail) {
        return buildResponseWithFields(status, code, detail, null);
    }

    private ResponseEntity<ProblemDetail> buildResponseWithFields(HttpStatus status, String code, String detail, Map<String, String> fields) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        
        if (fields != null) {
            pd.setProperty("fields", fields);
        }
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
            headers.add("X-Correlation-ID", traceId);
        }
        
        return new ResponseEntity<>(pd, headers, status);
    }
}

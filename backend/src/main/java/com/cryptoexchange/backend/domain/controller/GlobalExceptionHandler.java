package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.exception.InvalidOrderException;
import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private String getRequestId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private String getRequestPath() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRequestURI();
        }
        return "unknown";
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("NOT_FOUND", ex.getMessage(), getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalanceException(InsufficientBalanceException ex) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("INSUFFICIENT_BALANCE", ex.getMessage(), getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderException(InvalidOrderException ex) {
        log.warn("Invalid order: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("INVALID_ORDER", ex.getMessage(), getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(TransactionService.PriceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePriceUnavailableException(TransactionService.PriceUnavailableException ex) {
        log.warn("Price unavailable: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("PRICE_UNAVAILABLE", ex.getMessage(), getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid input: {}", ex.getMessage());
        String message = ex.getMessage();
        // Check if it's a duplicate user error
        if (message != null && (message.contains("already exists") || message.contains("User with"))) {
            ErrorResponse error = new ErrorResponse("USER_EXISTS", message, getRequestPath(), getRequestId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        ErrorResponse error = new ErrorResponse("INVALID_INPUT", message, getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("ACCESS_DENIED", "Access denied", getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.debug("Validation failed: {}", ex.getBindingResult().getAllErrors());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Validation failed", errors, getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = "An unexpected error occurred";
        if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
            message = ex.getMessage();
        }
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", message, getRequestPath(), getRequestId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    public static class ErrorResponse {
        private final String code;
        private final String message;
        private final Map<String, String> details;
        private final OffsetDateTime timestamp;
        private final String path;
        private final String traceId;

        public ErrorResponse(String code, String message) {
            this(code, message, null, null, null);
        }

        public ErrorResponse(String code, String message, String path, String traceId) {
            this(code, message, null, path, traceId);
        }

        public ErrorResponse(String code, String message, Map<String, String> details, String path, String traceId) {
            this.code = code;
            this.message = message;
            this.details = details;
            this.timestamp = OffsetDateTime.now();
            this.path = path;
            this.traceId = traceId;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, String> getDetails() {
            return details;
        }

        public OffsetDateTime getTimestamp() {
            return timestamp;
        }

        public String getPath() {
            return path;
        }

        public String getTraceId() {
            return traceId;
        }
    }
}

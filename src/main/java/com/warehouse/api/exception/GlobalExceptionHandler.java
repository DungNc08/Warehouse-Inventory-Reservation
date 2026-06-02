package com.warehouse.api.exception;

import com.warehouse.api.dto.ApiError;
import com.warehouse.api.dto.ApiResponse;
import com.warehouse.exception.BusinessException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern DUPLICATE_ORDER_ID = Pattern.compile("\\(order_id\\)=\\(([^)]+)\\)");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = resolveStatus(exception.getCode());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(new ApiError(exception.getCode(), exception.getMessage())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String orderId = extractDuplicateOrderId(exception);
        if (orderId != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(new ApiError(
                            "DUPLICATE_ORDER",
                            "A reservation already exists for order " + orderId
                    )));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(new ApiError("CONFLICT", "Request conflicts with existing data")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Validation failed";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiError("VALIDATION_ERROR", message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(new ApiError("INTERNAL_ERROR", "An unexpected error occurred")));
    }

    private String extractDuplicateOrderId(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && isDuplicateOrderIdViolation(message)) {
                Matcher matcher = DUPLICATE_ORDER_ID.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return null;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private boolean isDuplicateOrderIdViolation(String message) {
        String lower = message.toLowerCase();
        return lower.contains("order_id") && (lower.contains("duplicate") || lower.contains("unique"));
    }

    private HttpStatus resolveStatus(String code) {
        return switch (code) {
            case "INSUFFICIENT_STOCK", "INVALID_STATE_TRANSITION", "DUPLICATE_ORDER" -> HttpStatus.CONFLICT;
            case "RESERVATION_NOT_FOUND", "INVENTORY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}

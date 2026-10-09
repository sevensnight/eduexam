package com.eduexam.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Getter
    public static class DetailError {
        private final String detail;
        DetailError(String detail) { this.detail = detail; }
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<DetailError> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new DetailError(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<DetailError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new DetailError("权限不足"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<DetailError> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new DetailError("Invalid authentication credentials"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DetailError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new DetailError(msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DetailError> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DetailError("服务器内部错误: " + ex.getMessage()));
    }
}

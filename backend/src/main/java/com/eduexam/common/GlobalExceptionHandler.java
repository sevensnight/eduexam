package com.eduexam.common;

import lombok.Getter;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        return ResponseEntity.badRequest().body(new DetailError(msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<DetailError> handleBinding(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new DetailError(msg.isBlank() ? "请求参数不合法" : msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<DetailError> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new DetailError(msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DetailError> handleUnreadableJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new DetailError("请求 JSON 格式或字段类型不正确"));
    }

    @ExceptionHandler({TypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<DetailError> handleBadParameter(Exception ex) {
        return ResponseEntity.badRequest().body(new DetailError("请求参数类型不正确或缺少必填参数"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<DetailError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database constraint rejected a request: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new DetailError("数据已存在、正在被使用，或与其他数据冲突"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<DetailError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new DetailError("上传文件超过 10MB 限制"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<DetailError> handleMultipart(MultipartException ex) {
        return ResponseEntity.badRequest().body(new DetailError("上传文件请求格式不正确"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<DetailError> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new DetailError("不支持的请求内容类型"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DetailError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new DetailError(ex.getMessage() != null ? ex.getMessage() : "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DetailError> handleGeneral(Exception ex) {
        log.error("Unhandled server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DetailError("服务器内部错误，请稍后重试"));
    }
}

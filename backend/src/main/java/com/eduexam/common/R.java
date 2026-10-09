package com.eduexam.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一返回格式，兼容FastAPI的{"detail":"..."}错误格式
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    private T data;
    private String detail;

    private R() {}

    public static <T> T ok(T data) {
        return data;
    }

    public static ErrorBody error(String message) {
        return new ErrorBody(message);
    }

    @Getter
    public static class ErrorBody {
        private final String detail;
        ErrorBody(String detail) { this.detail = detail; }
    }
}

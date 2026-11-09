package com.gehu.sfs.exception;

import org.springframework.http.HttpStatus;

/** One exception type carrying an HTTP status; mapped to JSON by GlobalExceptionHandler. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ApiException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public static ApiException notFound(String m) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", m); }
    public static ApiException forbidden(String m) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", m); }
    public static ApiException conflict(String code, String m) { return new ApiException(HttpStatus.CONFLICT, code, m); }
    public static ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", m); }
}

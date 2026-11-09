package com.gehu.sfs.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<Map<String, String>> body(HttpStatus s, String code, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", code, "message", msg));
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> api(ApiException e) { return body(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(AllocationFailedException.class)
    public ResponseEntity<?> alloc(AllocationFailedException e) { return body(HttpStatus.INSUFFICIENT_STORAGE, "ALLOCATION_FAILED", e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> valid(MethodArgumentNotValidException e) {
        String m = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).orElse("Invalid request");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> big(MaxUploadSizeExceededException e) { return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the maximum upload size"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> dup(DataIntegrityViolationException e) { return body(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION", "Operation conflicts with existing data (duplicate or in use)"); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(AccessDeniedException e) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource"); }
}

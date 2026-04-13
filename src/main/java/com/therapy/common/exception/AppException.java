package com.therapy.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AppException notFound(String resource) {
        return new AppException(resource + " no encontrado", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static AppException forbidden() {
        return new AppException("Acceso no autorizado", HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static AppException badRequest(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    public static AppException conflict(String message) {
        return new AppException(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    public static AppException unauthorized(String message) {
        return new AppException(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}

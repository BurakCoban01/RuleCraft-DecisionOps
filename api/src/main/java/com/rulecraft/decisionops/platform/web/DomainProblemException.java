package com.rulecraft.decisionops.platform.web;

import org.springframework.http.HttpStatus;

public class DomainProblemException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainProblemException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}

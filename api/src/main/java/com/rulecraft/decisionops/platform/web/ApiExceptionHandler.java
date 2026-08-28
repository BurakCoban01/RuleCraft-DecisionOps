package com.rulecraft.decisionops.platform.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        var detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The request could not be completed. Try again or contact an administrator.");
        detail.setTitle("Unexpected request failure");
        detail.setType(URI.create("urn:rulecraft:problem:unexpected"));
        detail.setProperty("code", "RC-PLATFORM-UNEXPECTED");
        return detail;
    }
}

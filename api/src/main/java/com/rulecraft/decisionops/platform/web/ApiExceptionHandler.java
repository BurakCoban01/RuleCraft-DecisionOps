package com.rulecraft.decisionops.platform.web;

import java.net.URI;
import java.util.stream.Collectors;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainProblemException.class)
    ProblemDetail handleDomain(DomainProblemException exception) {
        return problem(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(), (a, b) -> a));
        var detail = problem(HttpStatus.BAD_REQUEST, "RC-VALIDATION-INVALID", "Review the highlighted fields and try again.");
        detail.setProperty("fields", fields);
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "RC-DATA-CONFLICT", "That change conflicts with existing data.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticConflict(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "RC-DATA-STALE", "The record changed while you were editing it. Refresh and try again.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "RC-REQUEST-MALFORMED", "The request body is malformed or contains an unsupported value.");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleRequestValue(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "RC-REQUEST-VALUE", "A path or query value is missing or invalid.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "RC-PLATFORM-UNEXPECTED",
                "The request could not be completed. Try again or contact an administrator.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("urn:rulecraft:problem:" + code.toLowerCase(Locale.ROOT)));
        detail.setProperty("code", code);
        return detail;
    }
}

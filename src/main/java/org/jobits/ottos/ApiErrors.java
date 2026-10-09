package org.jobits.ottos;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every error the API returns is a ProblemDetail with a stable {@code code}: the one an {@link ApiException} carries,
 * or one derived here for errors raised by Spring (validation, unreadable body, unknown route…).
 * Errors raised by Spring Security (401, 403) get the same shape from the identity module's security handlers.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

    static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** Two requests changed the same record at once: the loser gets a 409 and may retry, instead of a 500. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(OptimisticLockingFailureException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The record was changed by another request at the same time; reload it and try again");
        problem.setProperty(ApiException.CODE, "CONCURRENT_UPDATE");
        return problem;
    }

    /**
     * Invalid request parameter or header on a {@code @Validated} controller (e.g. size=500 breaks Max): checked by
     * method validation, which throws ConstraintViolationException instead of a Spring MVC exception.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters.");
        problem.setProperty(ApiException.CODE, VALIDATION_FAILED);
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath()),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
                .toList());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addCode(problem, ex, status);
        }
        return response;
    }

    private static void addCode(ProblemDetail problem, Exception ex, HttpStatusCode status) {
        Map<String, Object> properties = problem.getProperties();
        if (properties != null && properties.containsKey(ApiException.CODE)) {
            return;
        }
        if (ex instanceof MethodArgumentNotValidException invalid) {
            problem.setProperty(ApiException.CODE, VALIDATION_FAILED);
            problem.setProperty("errors", fieldErrors(invalid));
        } else if (ex instanceof HandlerMethodValidationException invalid) {
            problem.setProperty(ApiException.CODE, VALIDATION_FAILED);
            problem.setProperty("errors", parameterErrors(invalid));
        } else if (ex instanceof HttpMessageNotReadableException) {
            problem.setProperty(ApiException.CODE, "MALFORMED_REQUEST");
        } else if (ex instanceof TypeMismatchException) {
            problem.setProperty(ApiException.CODE, "INVALID_PARAMETER");
        } else {
            HttpStatus known = HttpStatus.resolve(status.value());
            problem.setProperty(ApiException.CODE, known != null ? known.name() : "HTTP_" + status.value());
        }
    }

    /** One entry per invalid field of the body: the field and the constraint it broke (NotBlank, Size, Email…). */
    private static List<FieldViolation> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), constraint(e)))
                .toList();
    }

    /** One entry per invalid request parameter or header (e.g. size=500 breaks Max). */
    private static List<FieldViolation> parameterErrors(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                violations.add(new FieldViolation(result.getMethodParameter().getParameterName(), constraint(error)))));
        return violations;
    }

    private static String lastNode(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    private static String constraint(MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError && fieldError.getCode() != null) {
            return fieldError.getCode();
        }
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
    }

    record FieldViolation(String field, String constraint) {
    }
}

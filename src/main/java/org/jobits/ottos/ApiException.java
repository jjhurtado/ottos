package org.jobits.ottos;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An error returned to the client as a ProblemDetail with a stable {@code code} (e.g. AMOUNT_TOO_SMALL) next to the
 * English {@code detail}. Clients branch on and translate the code; the detail is for people reading logs.
 * Codes are part of the API contract: never rename one, add a new one instead.
 */
public class ApiException extends ResponseStatusException {

    public static final String CODE = "code";

    private final String code;
    private final HttpHeaders headers = new HttpHeaders();

    public ApiException(HttpStatus status, String code, String detail) {
        super(status, detail);
        this.code = code;
        getBody().setProperty(CODE, code);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException unauthorized(String code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException tooManyRequests(String code, String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, detail);
    }

    /** Adds a value the client can use to explain the error, e.g. {@code with("minimumAmount", 20)}. */
    public ApiException with(String property, Object value) {
        getBody().setProperty(property, value);
        return this;
    }

    /** Adds a response header, e.g. {@code withHeader("Retry-After", "900")}. */
    public ApiException withHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    public String getCode() {
        return code;
    }
}

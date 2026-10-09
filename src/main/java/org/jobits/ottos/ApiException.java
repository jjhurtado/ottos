package org.jobits.ottos;

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

    public String getCode() {
        return code;
    }
}

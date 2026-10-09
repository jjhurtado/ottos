package org.jobits.ottos;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Two requests changed the same record at once: the loser gets a 409 and may retry, instead of a 500. */
@RestControllerAdvice
class ConcurrencyErrors {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The record was changed by another request at the same time; reload it and try again");
    }
}

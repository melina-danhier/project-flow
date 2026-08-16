package de.melinadanhier.projectflow.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbidden(ForbiddenOperationException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail handleValidation(DomainValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleOptimisticLock(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT,
                "Die Ressource wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}

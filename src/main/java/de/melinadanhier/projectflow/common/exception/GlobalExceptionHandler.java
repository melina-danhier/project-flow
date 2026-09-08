package de.melinadanhier.projectflow.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException exception) {
        return page("error/404", HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ModelAndView handleForbidden(ForbiddenOperationException exception) {
        return page("error/error", HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ModelAndView handleConflict(ConflictException exception) {
        return page("error/409", HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DomainValidationException.class)
    public ModelAndView handleValidation(DomainValidationException exception) {
        return page("error/error", HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ModelAndView handleOptimisticLock(RuntimeException exception) {
        return page("error/409", HttpStatus.CONFLICT,
                "Die Ressource wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView handlePersistenceFailure(DataAccessException exception) {
        log.error("Unerwarteter Persistenzfehler bei der Verarbeitung einer Webanfrage.", exception);
        return page("error/500", HttpStatus.INTERNAL_SERVER_ERROR,
                "Die Daten konnten gerade nicht verarbeitet werden. Bitte versuche es später erneut.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpectedFailure(Exception exception) {
        if (exception instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_REQUEST;
            }
            return page(clientErrorView(status), status, clientErrorMessage(status));
        }
        log.error("Unerwarteter Fehler bei der Verarbeitung einer Webanfrage.", exception);
        return page("error/500", HttpStatus.INTERNAL_SERVER_ERROR,
                "Ein unerwarteter Fehler ist aufgetreten. Bitte versuche es später erneut.");
    }

    private ModelAndView page(String viewName, HttpStatus status, String message) {
        ModelAndView view = new ModelAndView(viewName, status);
        view.addObject("statusCode", status.value());
        view.addObject("errorMessage", message);
        return view;
    }

    private String clientErrorView(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "error/404";
            case CONFLICT -> "error/409";
            default -> "error/error";
        };
    }

    private String clientErrorMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Der angeforderte Inhalt wurde nicht gefunden.";
            case METHOD_NOT_ALLOWED -> "Diese Aktion ist über den verwendeten Aufruf nicht möglich.";
            case FORBIDDEN -> "Du darfst diese Aktion nicht ausführen.";
            default -> "Die Anfrage konnte nicht verarbeitet werden. Bitte prüfe deine Eingaben.";
        };
    }
}

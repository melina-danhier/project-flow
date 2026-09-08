package de.melinadanhier.projectflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsKnownClientErrorsToDedicatedPages() {
        ModelAndView notFound = handler.handleNotFound(
                new ResourceNotFoundException("Das Projekt wurde nicht gefunden."));
        ModelAndView conflict = handler.handleConflict(
                new ConflictException("Das Projekt wurde zwischenzeitlich geändert."));

        assertThat(notFound.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getViewName()).isEqualTo("error/404");
        assertThat(notFound.getModel().get("errorMessage"))
                .isEqualTo("Das Projekt wurde nicht gefunden.");
        assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getViewName()).isEqualTo("error/409");
    }

    @Test
    void persistenceAndUnexpectedFailuresNeverExposeInternalDetails() {
        String internalDetail = "password=secret; SQLSTATE=23505";

        ModelAndView persistence = handler.handlePersistenceFailure(
                new DataIntegrityViolationException(internalDetail));
        ModelAndView unexpected = handler.handleUnexpectedFailure(
                new IllegalStateException(internalDetail));

        assertSafeServerError(persistence, internalDetail,
                "Die Daten konnten gerade nicht verarbeitet werden. Bitte versuche es später erneut.");
        assertSafeServerError(unexpected, internalDetail,
                "Ein unerwarteter Fehler ist aufgetreten. Bitte versuche es später erneut.");
    }

    private void assertSafeServerError(ModelAndView result, String internalDetail,
                                       String expectedMessage) {
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getViewName()).isEqualTo("error/500");
        assertThat(result.getModel().get("errorMessage"))
                .isEqualTo(expectedMessage)
                .asString().doesNotContain(internalDetail);
    }
}

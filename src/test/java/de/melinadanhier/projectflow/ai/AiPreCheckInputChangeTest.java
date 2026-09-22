package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;
import de.melinadanhier.projectflow.wizard.service.ProjectQuestionCatalogLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AiPreCheckInputChangeTest {

    @BeforeAll
    static void initCatalog() {
        new ProjectQuestionCatalogLoader();
    }

    @ParameterizedTest
    @CsvSource({
            "workingTime, Verfügbare Arbeitszeit",
            "availableWorkingTime, Verfügbare Arbeitszeit",
            "startDate, Startdatum",
            "endDate, Enddatum",
            "category, Kategorie",
            "subcategory, Unterkategorie",
            "projectGoal, Projektziel",
            "constraints, Rahmenbedingungen",
            "additionalInformation, Weitere Hinweise",
            "durationDays, Dauer",
            "title, Titel",
            "description, Beschreibung",
            "scope, Umfang",
            "projectSpecificAnswers.scope, Umfang",
            "userPreCheckCorrection1, Ergänzung zur Planung",
            "someUnknownKey, Weitere Angabe"
    })
    void fieldLabelReturnsUserFriendlyGermanLabel(String field, String expectedLabel) {
        var change = new AiPreCheckInputChange(field, "Bisheriger Wert", "Neuer Wert");
        assertThat(change.fieldLabel()).isEqualTo(expectedLabel);
    }

    @Test
    void displayValuesFormatDatesCorrectly() {
        var change = new AiPreCheckInputChange("startDate", "2026-10-01", "2026-10-15");
        assertThat(change.displayPreviousValue()).isEqualTo("01.10.2026");
        assertThat(change.displayNewValue()).isEqualTo("15.10.2026");
    }

    @Test
    void displayValuesPreserveNonDateText() {
        var change = new AiPreCheckInputChange("workingTime", "5 Stunden pro Woche", "10 Stunden pro Woche");
        assertThat(change.displayPreviousValue()).isEqualTo("5 Stunden pro Woche");
        assertThat(change.displayNewValue()).isEqualTo("10 Stunden pro Woche");
    }
}

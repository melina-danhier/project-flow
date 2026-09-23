package de.melinadanhier.projectflow.wizard.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiWizardSummaryTest {

    @Test
    void formatsWeeksProperly() {
        AiWizardSummary singular = summaryWith(7, 1, "WEEKS");
        assertThat(singular.formattedDuration()).isEqualTo("1 Woche");

        AiWizardSummary plural = summaryWith(42, 6, "WEEKS");
        assertThat(plural.formattedDuration()).isEqualTo("6 Wochen");
    }

    @Test
    void formatsMonthsProperly() {
        AiWizardSummary singular = summaryWith(30, 1, "MONTHS");
        assertThat(singular.formattedDuration()).isEqualTo("1 Monat");

        AiWizardSummary plural = summaryWith(60, 2, "MONTHS");
        assertThat(plural.formattedDuration()).isEqualTo("2 Monate");
    }

    @Test
    void formatsDaysProperly() {
        AiWizardSummary singular = summaryWith(1, 1, "DAYS");
        assertThat(singular.formattedDuration()).isEqualTo("1 Tag");

        AiWizardSummary plural = summaryWith(14, 14, "DAYS");
        assertThat(plural.formattedDuration()).isEqualTo("14 Tage");
    }

    @Test
    void fallsBackToDurationDaysWhenValueOrUnitMissing() {
        AiWizardSummary daysOnlySingular = summaryWith(1, null, null);
        assertThat(daysOnlySingular.formattedDuration()).isEqualTo("1 Tag");

        AiWizardSummary daysOnlyPlural = summaryWith(21, null, null);
        assertThat(daysOnlyPlural.formattedDuration()).isEqualTo("21 Tage");
    }

    @Test
    void returnsNullWhenNoDurationProvided() {
        AiWizardSummary none = summaryWith(null, null, null);
        assertThat(none.formattedDuration()).isNull();
    }

    private AiWizardSummary summaryWith(Integer days, Integer value, String unit) {
        return new AiWizardSummary(
                "Titel", "Beschreibung", null, null, false, "Kategorie", "KI",
                days, value, unit, null, null, null, null, List.of()
        );
    }
}

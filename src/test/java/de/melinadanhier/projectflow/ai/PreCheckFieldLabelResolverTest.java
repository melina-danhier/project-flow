package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.precheck.PreCheckFieldLabelResolver;
import de.melinadanhier.projectflow.wizard.service.ProjectQuestionCatalogLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PreCheckFieldLabelResolverTest {

    @BeforeAll
    static void initQuestionCatalog() {
        new ProjectQuestionCatalogLoader();
    }

    @ParameterizedTest
    @CsvSource({
            "workingTime, Verfügbare Arbeitszeit",
            "availableWorkingTime, Verfügbare Arbeitszeit",
            "availableTime, Verfügbare Arbeitszeit",
            "startDate, Startdatum",
            "endDate, Enddatum",
            "durationDays, Dauer",
            "duration, Dauer",
            "projectGoal, Projektziel",
            "constraints, Rahmenbedingungen",
            "additionalInformation, Weitere Hinweise",
            "category, Kategorie",
            "subcategory, Unterkategorie",
            "collaborationMode, Projektart",
            "title, Titel",
            "description, Beschreibung",
            "rejectedElements, Abgelehnte Elemente"
    })
    void resolvesStandardPreCheckFields(String fieldKey, String expectedLabel) {
        assertThat(PreCheckFieldLabelResolver.resolveLabel(fieldKey)).isEqualTo(expectedLabel);
    }

    @ParameterizedTest
    @CsvSource({
            "scope, Umfang",
            "projectSpecificAnswers.scope, Umfang",
            "topic, Thema",
            "projectSpecificAnswers.topic, Thema",
            "budget, Budget",
            "projectSpecificAnswers.budget, Budget",
            "venue, Veranstaltungsort",
            "projectSpecificAnswers.venue, Veranstaltungsort",
            "movingSituation, Umzugssituation",
            "projectSpecificAnswers.movingSituation, Umzugssituation",
            "householdScope, Umfang des Haushalts",
            "projectSpecificAnswers.householdScope, Umfang des Haushalts",
            "transportAndHelp, Transport & Helfer",
            "projectSpecificAnswers.transportAndHelp, Transport & Helfer",
            "declutterOrRenovate, Vorarbeiten & Renovieren",
            "projectSpecificAnswers.declutterOrRenovate, Vorarbeiten & Renovieren"
    })
    void resolvesProjectSpecificQuestionKeysWithAndWithoutPrefix(String fieldKey, String expectedLabel) {
        assertThat(PreCheckFieldLabelResolver.resolveLabel(fieldKey)).isEqualTo(expectedLabel);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "userPreCheckCorrection1",
            "userPreCheckCorrection2",
            "userPreCheckCorrection99",
            "projectSpecificAnswers.userPreCheckCorrection1",
            "projectSpecificAnswers.userPreCheckCorrection5"
    })
    void resolvesUserPreCheckCorrectionsByPattern(String fieldKey) {
        assertThat(PreCheckFieldLabelResolver.resolveLabel(fieldKey)).isEqualTo("Ergänzung zur Planung");
    }

    @Test
    void resolvesDynamicQuestionFromCatalogWhenNotInShortLabelMap() {
        // "disposalOptions" ist im YAML-Katalog (HOME.DECLUTTERING), aber nicht im festen Kurzlabel-Map
        String label = PreCheckFieldLabelResolver.resolveLabel("projectSpecificAnswers.disposalOptions");
        assertThat(label).isEqualTo("Was passiert mit den aussortierten Dingen?");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "unknownField",
            "customFutureKey",
            "projectSpecificAnswers.nonExistentQuestion",
            "newBackendFeatureKey"
    })
    void usesSafeFallbackForUnknownOrFutureKeys(String unknownKey) {
        assertThat(PreCheckFieldLabelResolver.resolveLabel(unknownKey))
                .isEqualTo(PreCheckFieldLabelResolver.DEFAULT_FALLBACK_LABEL)
                .isEqualTo("Weitere Angabe");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void handlesNullOrBlankGracefully(String emptyKey) {
        assertThat(PreCheckFieldLabelResolver.resolveLabel(emptyKey))
                .isEqualTo(PreCheckFieldLabelResolver.DEFAULT_FALLBACK_LABEL)
                .isEqualTo("Weitere Angabe");
    }
}

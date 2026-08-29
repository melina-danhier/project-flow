package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientRequestTest {

    @Test
    void requiredConfirmedWizardDataCannotBeNull() {
        assertThatThrownBy(() -> new AiPreCheckRequest(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiGenerationRequest(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void missingAcknowledgedWarningsAreNormalizedToAnEmptyImmutableList() {
        AiGenerationRequest request = new AiGenerationRequest(snapshot(), null);

        assertThat(request.acknowledgedWarnings()).isEmpty();
        assertThatThrownBy(() -> request.acknowledgedWarnings().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acknowledgedWarningsAreCopiedWithoutChangingContentOrOrder() {
        var warning = new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Knapp", "Mehr Zeit einplanen");
        var otherWarning = new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Budget knapp", "Umfang reduzieren");
        var supplied = new java.util.ArrayList<>(List.of(warning, otherWarning));
        var request = new AiGenerationRequest(snapshot(), supplied);
        supplied.clear();

        assertThat(request.acknowledgedWarnings()).containsExactly(warning, otherWarning);
        assertThatThrownBy(() -> request.acknowledgedWarnings().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AiPreCheckSeverity.class, names = "ERROR")
    @NullSource
    void rejectsNonWarningSeverityInsteadOfSilentlyFilteringIt(AiPreCheckSeverity severity) {
        var warning = new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Knapp", "Mehr Zeit einplanen");
        var invalid = new AiPreCheckProblem(severity, "Unmöglich", "Ziel ändern");

        assertThatThrownBy(() -> new AiGenerationRequest(snapshot(), List.of(warning, invalid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullWarningElements() {
        assertThatThrownBy(() -> new AiGenerationRequest(snapshot(), java.util.Arrays.asList((AiPreCheckProblem) null)))
                .isInstanceOf(NullPointerException.class);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Testprojekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, null, "Test",
                null, null, null);
    }
}

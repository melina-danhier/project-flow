package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientRequestTest {

    @Test
    void requiredConfirmedWizardDataCannotBeNull() {
        assertThatThrownBy(() -> new AiPreCheckRequest(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("confirmedWizardData");
        assertThatThrownBy(() -> new AiGenerationRequest(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("confirmedWizardData");
    }

    @Test
    void missingIgnoredWarningsAreNormalizedToAnEmptyImmutableList() {
        AiGenerationRequest request = new AiGenerationRequest(snapshot(), null);

        assertThat(request.explicitlyIgnoredWarnings()).isEmpty();
        assertThatThrownBy(() -> request.explicitlyIgnoredWarnings().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Testprojekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }
}

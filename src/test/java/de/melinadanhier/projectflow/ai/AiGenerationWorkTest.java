package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AiGenerationWorkTest {

    @Test
    void defensivelyCopiesAllLists() {
        var warnings = new ArrayList<>(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Problem", "Hinweis")));
        var work = new AiGenerationWork(UUID.randomUUID(), UUID.randomUUID(), null,
                warnings, 0);
        warnings.clear();

        assertThat(work.acceptedOpenPoints()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> work.acceptedOpenPoints().clear());
    }

    @Test
    void normalizesNullListsToEmptyLists() {
        var work = new AiGenerationWork(UUID.randomUUID(), UUID.randomUUID(), null,
                null, 0);

        assertThat(work.acceptedOpenPoints()).isEmpty();
    }
}

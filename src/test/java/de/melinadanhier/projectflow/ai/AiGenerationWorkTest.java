package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;
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
        var confirmed = new ArrayList<>(List.of("Bestätigt"));
        var rejected = new ArrayList<>(List.of(
                new RejectedCriticalAssumption("Annahme", "Korrektur")));

        var work = new AiGenerationWork(UUID.randomUUID(), UUID.randomUUID(), null,
                warnings, confirmed, rejected, 0);
        warnings.clear();
        confirmed.clear();
        rejected.clear();

        assertThat(work.acknowledgedWarnings()).hasSize(1);
        assertThat(work.confirmedAssumptions()).containsExactly("Bestätigt");
        assertThat(work.rejectedAssumptions()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> work.confirmedAssumptions().clear());
    }

    @Test
    void normalizesNullListsToEmptyLists() {
        var work = new AiGenerationWork(UUID.randomUUID(), UUID.randomUUID(), null,
                null, null, null, 0);

        assertThat(work.acknowledgedWarnings()).isEmpty();
        assertThat(work.confirmedAssumptions()).isEmpty();
        assertThat(work.rejectedAssumptions()).isEmpty();
    }
}

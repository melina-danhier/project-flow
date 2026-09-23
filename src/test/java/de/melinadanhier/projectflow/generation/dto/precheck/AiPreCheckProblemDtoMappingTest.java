package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPreCheckProblemDtoMappingTest {

    @Test
    void mapsTypeLabelsCorrectly() {
        assertThat(createDto(AiPreCheckProblemType.CRITICAL_ASSUMPTION, AiPreCheckSeverity.WARNING).getTypeLabel())
                .isEqualTo("Empfohlene Anpassung");

        assertThat(createDto(AiPreCheckProblemType.ASSUMPTION, AiPreCheckSeverity.WARNING).getTypeLabel())
                .isEqualTo("Planungsannahme");

        assertThat(createDto(AiPreCheckProblemType.RISK, AiPreCheckSeverity.WARNING).getTypeLabel())
                .isEqualTo("Planungsrisiko");

        assertThat(createDto(AiPreCheckProblemType.CONFLICT, AiPreCheckSeverity.ERROR).getTypeLabel())
                .isEqualTo("Widerspruch");
    }

    @Test
    void mapsActionLabelsCorrectly() {
        assertThat(createDto(AiPreCheckProblemType.CRITICAL_ASSUMPTION, AiPreCheckSeverity.WARNING).getActionLabel())
                .isEqualTo("Vorgeschlagene Änderung übernehmen");

        assertThat(createDto(AiPreCheckProblemType.ASSUMPTION, AiPreCheckSeverity.WARNING).getActionLabel())
                .isEqualTo("Bestätigen");

        assertThat(createDto(AiPreCheckProblemType.RISK, AiPreCheckSeverity.WARNING).getActionLabel())
                .isEqualTo("Bestätigen");

        assertThat(createDto(AiPreCheckProblemType.CONFLICT, AiPreCheckSeverity.ERROR).getActionLabel())
                .isNull();
    }

    private AiPreCheckProblemDto createDto(AiPreCheckProblemType type, AiPreCheckSeverity severity) {
        return new AiPreCheckProblemDto(
                0,
                severity,
                type,
                "Test message",
                "Test action",
                "Test interpretation",
                false,
                List.of(),
                true
        );
    }
}

package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPreCheckProblemDtoTest {

    @Test
    void cleansDuplicateSuggestedUserActionPrefixes() {
        var dto1 = new AiPreCheckProblemDto(
                0, AiPreCheckSeverity.WARNING, AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Mögliche Anpassungen: Ein Umzugsunternehmen für den Transport beauftragen.",
                "Planungsgrundlage", false, List.of(), true);

        assertThat(dto1.suggestedUserAction())
                .isEqualTo("Ein Umzugsunternehmen für den Transport beauftragen.");

        var dto2 = new AiPreCheckProblemDto(
                1, AiPreCheckSeverity.WARNING, AiPreCheckProblemType.RISK,
                "Problem",
                "Mögliche Anpassung: Mehr Arbeitszeit einplanen.",
                "Planungsgrundlage", false, List.of(), true);

        assertThat(dto2.suggestedUserAction())
                .isEqualTo("Mehr Arbeitszeit einplanen.");

        var dto3 = new AiPreCheckProblemDto(
                2, AiPreCheckSeverity.WARNING, AiPreCheckProblemType.RISK,
                "Problem",
                "Vorschlag: Den Umfang reduzieren.",
                "Planungsgrundlage", false, List.of(), true);

        assertThat(dto3.suggestedUserAction())
                .isEqualTo("Den Umfang reduzieren.");

        var dto4 = new AiPreCheckProblemDto(
                3, AiPreCheckSeverity.WARNING, AiPreCheckProblemType.RISK,
                "Problem",
                "Mehr Arbeitszeit einplanen oder Umfang reduzieren.",
                "Planungsgrundlage", false, List.of(), true);

        assertThat(dto4.suggestedUserAction())
                .isEqualTo("Mehr Arbeitszeit einplanen oder Umfang reduzieren.");
    }

    @Test
    void handlesNullAndBlankGracefully() {
        assertThat(AiPreCheckProblemDto.cleanSuggestedUserAction(null)).isNull();
        assertThat(AiPreCheckProblemDto.cleanSuggestedUserAction("")).isEqualTo("");
        assertThat(AiPreCheckProblemDto.cleanSuggestedUserAction("   ")).isEqualTo("   ");
    }
}

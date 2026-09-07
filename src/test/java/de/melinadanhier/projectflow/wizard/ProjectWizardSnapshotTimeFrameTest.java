package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProjectWizardSnapshotTimeFrameTest {

    private final ProjectWizardService service = new ProjectWizardService();

    @Test
    void storesAndRestoresAllIndependentTimeInputs() {
        UUID userId = UUID.randomUUID();
        var snapshot = snapshot(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
                28, "Montag bis Freitag jeweils etwa 2 Stunden", "Wichtigster Zusatz");
        var session = new MockHttpSession();

        service.restoreFromSnapshot(snapshot, userId, session);
        ProjectBasicsForm restored = ProjectBasicsForm.from(service.requireOwned(userId, session));

        assertThat(restored.getStartDate()).isEqualTo(snapshot.startDate());
        assertThat(restored.getEndDate()).isEqualTo(snapshot.endDate());
        assertThat(restored.getDurationDays()).isEqualTo(28);
        assertThat(restored.getAvailableWorkingTime()).isEqualTo(snapshot.availableWorkingTime());
        assertThat(service.requireOwned(userId, session).getAdditionalInformation())
                .isEqualTo("Wichtigster Zusatz");
    }

    @Test
    void keepsAllOptionalTimeValuesNull() {
        var snapshot = snapshot(null, null, null, null, null);
        assertThat(snapshot.startDate()).isNull();
        assertThat(snapshot.endDate()).isNull();
        assertThat(snapshot.durationDays()).isNull();
        assertThat(snapshot.availableWorkingTime()).isNull();
    }

    @Test
    void serializesAndRestoresTypedClassificationAndNewFields() {
        ProjectSubCategory subcategory = ProjectSubCategory.THESIS;
        var snapshot = new AiWizardSnapshot("Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, subcategory.getCategory(), subcategory, null,
                null, null, "Hinweis", 14, "8 Stunden pro Woche", Map.of("topics", "KI"));
        var codec = new AiWorkflowPayloadCodec(JsonMapper.builder().build());

        var restored = codec.readSnapshot(codec.writeSnapshot(snapshot));

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.subcategory()).isEqualTo(subcategory);
        assertThat(restored.availableWorkingTime()).isEqualTo("8 Stunden pro Woche");
    }

    @Test
    void readsLegacySnapshotWithObsoleteTimeFrameType() {
        var codec = new AiWorkflowPayloadCodec(JsonMapper.builder().build());
        String legacyJson = """
                {"title":"Altprojekt","startDate":"2026-09-01","endDate":"2026-09-10",
                 "collaborationMode":"INDIVIDUAL","category":"OTHER",
                 "otherProjectTypeDescription":"Test","timeFrameType":"START_AND_END"}
                """;

        AiWizardSnapshot restored = codec.readSnapshot(legacyJson);

        assertThat(restored.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(restored.endDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(restored.durationDays()).isNull();
        assertThat(restored.availableWorkingTime()).isNull();
    }

    @Test
    void rejectsOnlyRealTimeContradictions() {
        assertThatIllegalArgumentException().isThrownBy(() -> snapshot(
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> snapshot(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), 9, null, null));

        assertThat(snapshot(LocalDate.of(2026, 9, 1), null, null, null, null)).isNotNull();
        assertThat(snapshot(null, LocalDate.of(2026, 9, 10), null, null, null)).isNotNull();
        assertThat(snapshot(null, null, 10, null, null)).isNotNull();
        assertThat(snapshot(null, null, null, "5 Stunden am Wochenende", null)).isNotNull();
    }

    private AiWizardSnapshot snapshot(LocalDate start, LocalDate end, Integer duration,
                                      String workingTime, String additionalInformation) {
        return new AiWizardSnapshot("Projekt", null, start, end,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, null, "Test",
                null, null, additionalInformation, duration, workingTime, Map.of());
    }
}

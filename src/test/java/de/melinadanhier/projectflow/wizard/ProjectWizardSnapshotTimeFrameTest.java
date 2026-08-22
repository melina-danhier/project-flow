package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.request.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.service.ProjectTimeFrameCalculator;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectWizardSnapshotTimeFrameTest {

    private final ProjectWizardService service = new ProjectWizardService(new ProjectTimeFrameCalculator());

    @ParameterizedTest
    @EnumSource(ProjectTimeFrameType.class)
    void storesAndRestoresOriginalTimeFrameInputs(ProjectTimeFrameType mode) {
        UUID userId = UUID.randomUUID();
        MockHttpSession originalSession = new MockHttpSession();
        ProjectBasicsForm form = form(mode);
        service.saveBasics(form, userId, originalSession);
        var state = service.requireOwned(userId, originalSession);
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                state.getTitle(), state.getDescription(), state.getStartDate(), state.getEndDate(),
                state.getCollaborationMode(), state.getCategory(), state.getProjectType(),
                null, null, null, AiProjectTimeFrameType.valueOf(state.getTimeFrameType().name()),
                state.getDurationDays());

        MockHttpSession restoredSession = new MockHttpSession();
        service.restoreFromSnapshot(snapshot, UUID.randomUUID(), userId, restoredSession);
        ProjectBasicsForm restored = ProjectBasicsForm.from(service.requireOwned(userId, restoredSession));

        assertThat(restored.getTimeFrameType()).isEqualTo(mode);
        assertThat(restored.getDurationDays()).isEqualTo(form.getDurationDays());
        assertThat(restored.getStartDate()).isEqualTo(form.getStartDate());
        assertThat(restored.getEndDate()).isEqualTo(form.getEndDate());
    }

    @Test
    void fallsBackForLegacySnapshotWithoutMode() {
        UUID userId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        AiWizardSnapshot legacy = new AiWizardSnapshot(
                "Alt", null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Sonstiges",
                null, null, null);

        service.restoreFromSnapshot(legacy, UUID.randomUUID(), userId, session);

        assertThat(service.requireOwned(userId, session).getTimeFrameType())
                .isEqualTo(ProjectTimeFrameType.START_AND_END);
    }

    private ProjectBasicsForm form(ProjectTimeFrameType mode) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle("Projekt");
        form.setCategory(TemplateCategory.OTHER);
        form.setOtherProjectTypeDescription("Test");
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        form.setTimeFrameType(mode);
        switch (mode) {
            case START_AND_END -> {
                form.setStartDate(LocalDate.of(2026, 9, 1));
                form.setEndDate(LocalDate.of(2026, 9, 10));
            }
            case START_AND_DURATION -> {
                form.setStartDate(LocalDate.of(2026, 9, 1));
                form.setDurationDays(10);
            }
            case END_AND_DURATION -> {
                form.setEndDate(LocalDate.of(2026, 9, 10));
                form.setDurationDays(10);
            }
            case NONE -> { }
        }
        return form;
    }
}

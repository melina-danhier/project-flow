package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.AiProjectQuestionCatalog;
import de.melinadanhier.projectflow.wizard.service.ProjectTimeFrameCalculator;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiProjectSpecificDetailsTest {

    private final ProjectWizardService service = new ProjectWizardService(new ProjectTimeFrameCalculator());

    @Test
    void renovationAnswersReachSummarySnapshotAndPreCheckPayload() {
        Context context = context(ProjectSubCategory.RENOVATION_OR_HOME_PROJECT, CollaborationMode.INDIVIDUAL);
        AiProjectDetailsForm details = new AiProjectDetailsForm();
        details.setAnswers(Map.of(
                "affectedRooms", "80-m²-Wohnung",
                "plannedWork", "Vollständig streichen, Boden erneuern und Küche austauschen",
                "executionMode", "Eigenleistung",
                "specialConstraints", "Nur Samstag 08:00 bis Sonntag 20:00"));

        service.saveAiDetails(details, context.userId(), context.session());
        var summary = service.aiSummary(context.userId(), context.session());
        UUID token = service.completionToken(context.userId(), context.session());
        AiWizardSnapshot snapshot = service.confirmedSnapshot(token, context.userId(), context.session());
        String preCheckPayload = new PreCheckPromptBuilder(JsonMapper.builder().build())
                .build(snapshot).confirmedUserData();

        assertThat(summary.groupProject()).isFalse();
        assertThat(summary.projectSpecificAnswers()).extracting("key")
                .contains("affectedRooms", "plannedWork", "executionMode", "specialConstraints");
        assertThat(snapshot.projectSpecificAnswers()).containsEntry("affectedRooms", "80-m²-Wohnung");
        assertThat(preCheckPayload).contains("80-m²-Wohnung", "Boden erneuern", "Eigenleistung")
                .contains("\"collaborationMode\":\"INDIVIDUAL\"");
    }

    @Test
    void softwareQuestionsDoNotAcceptRenovationFields() {
        var questions = AiProjectQuestionCatalog.questionsFor(
                TemplateCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT);

        assertThat(questions).extracting("key")
                .contains("goalAndScope", "technologies", "technicalConstraints")
                .doesNotContain("affectedRooms", "plannedWork");
        assertThat(AiProjectQuestionCatalog.containsUnknownKey(
                TemplateCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT,
                Map.of("affectedRooms", "Wohnzimmer"))).isTrue();
        assertThat(AiProjectQuestionCatalog.sanitize(
                TemplateCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT,
                Map.of("goalAndScope", "Kleine Webanwendung", "affectedRooms", "Wohnzimmer")))
                .containsOnlyKeys("goalAndScope");
    }

    @Test
    void otherUsesGenericQuestionsWithoutInventingASubcategory() {
        assertThat(AiProjectQuestionCatalog.questionsFor(TemplateCategory.OTHER, null))
                .extracting("key")
                .containsExactly("desiredOutcome", "currentSituation", "relevantConditions", "specialConstraints");
    }

    @Test
    void changingClassificationDropsAnswersThatNoLongerApply() {
        Context context = context(ProjectSubCategory.RENOVATION_OR_HOME_PROJECT, CollaborationMode.GROUP);
        AiProjectDetailsForm details = new AiProjectDetailsForm();
        details.setAnswers(Map.of("affectedRooms", "Wohnzimmer"));
        service.saveAiDetails(details, context.userId(), context.session());

        ProjectBasicsForm changed = basics(ProjectSubCategory.SOFTWARE_PROJECT, CollaborationMode.GROUP);
        service.saveBasics(changed, context.userId(), context.session());
        ProjectWizardState state = service.requireOwned(context.userId(), context.session());

        assertThat(state.getProjectSpecificAnswers()).isEmpty();
        assertThat(state.isAiDetailsCompleted()).isFalse();
    }

    @Test
    void navigationRoundTripKeepsCollaborationModeAndAnswers() {
        Context context = context(ProjectSubCategory.SOFTWARE_PROJECT, CollaborationMode.GROUP);
        AiProjectDetailsForm details = new AiProjectDetailsForm();
        details.setAnswers(new LinkedHashMap<>(Map.of("technologies", "Java und Spring Boot")));
        service.saveAiDetails(details, context.userId(), context.session());

        assertThat(ProjectBasicsForm.from(service.requireOwned(context.userId(), context.session()))
                .getCollaborationMode()).isEqualTo(CollaborationMode.GROUP);
        assertThat(AiProjectDetailsForm.from(service.requireOwned(context.userId(), context.session()))
                .getAnswers()).containsEntry("technologies", "Java und Spring Boot");
    }

    private Context context(ProjectSubCategory subcategory, CollaborationMode collaborationMode) {
        UUID userId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        service.saveBasics(basics(subcategory, collaborationMode), userId, session);
        service.selectCreationType(CreationType.AI, userId, session);
        return new Context(userId, session);
    }

    private ProjectBasicsForm basics(ProjectSubCategory subcategory, CollaborationMode collaborationMode) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle("Testprojekt");
        form.setDescription("Beschreibung");
        form.setCategory(subcategory.getCategory());
        form.setSubcategory(subcategory);
        form.setCollaborationMode(collaborationMode);
        return form;
    }

    private record Context(UUID userId, MockHttpSession session) { }
}

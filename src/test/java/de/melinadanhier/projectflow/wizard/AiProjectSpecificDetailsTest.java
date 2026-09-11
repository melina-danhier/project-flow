package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.AiProjectQuestionCatalog;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiProjectSpecificDetailsTest {

    private final ProjectWizardService service = new ProjectWizardService();

    @Test
    void renovationAnswersReachSummarySnapshotAndPreCheckPayload() {
        Context context = context(ProjectSubCategory.RENOVATION_OR_HOME_PROJECT, CollaborationMode.INDIVIDUAL);
        AiProjectDetailsForm details = new AiProjectDetailsForm();
        details.setAnswers(Map.of(
                "affectedRooms", "80-m²-Wohnung",
                "plannedWork", "Vollständig streichen, Boden erneuern und Küche austauschen",
                "executionMode", "Eigenleistung",
                "budgetMaterials", "Bis 15.000 Euro, Farben sind vorhanden"));
        details.setAdditionalInformation("  Nachhaltige Materialien bevorzugen  ");

        service.saveAiDetails(details, context.userId(), context.session());
        var summary = service.aiSummary(context.userId(), context.session());
        UUID token = service.completionToken(context.userId(), context.session());
        AiWizardSnapshot snapshot = service.confirmedSnapshot(token, context.userId(), context.session());
        String preCheckPayload = new PreCheckPromptBuilder(JsonMapper.builder().build())
                .build(snapshot).confirmedUserData();

        assertThat(summary.groupProject()).isFalse();
        assertThat(summary.projectSpecificAnswers()).extracting("key")
                .containsExactly("affectedRooms", "plannedWork", "executionMode", "budgetMaterials");
        assertThat(snapshot.projectSpecificAnswers()).containsEntry("affectedRooms", "80-m²-Wohnung");
        assertThat(snapshot.additionalInformation()).isEqualTo("Nachhaltige Materialien bevorzugen");
        assertThat(preCheckPayload).contains("80-m²-Wohnung", "Boden erneuern", "Eigenleistung")
                .contains("Nachhaltige Materialien bevorzugen")
                .contains("\"collaborationMode\":\"INDIVIDUAL\"");
    }

    @Test
    void workingTimeIsSavedAndVisibleAsFirstAiWizardInput() {
        Context context = context(ProjectSubCategory.RENOVATION_OR_HOME_PROJECT, CollaborationMode.INDIVIDUAL);
        AiProjectDetailsForm details = new AiProjectDetailsForm();
        details.setAvailableWorkingTime("  5 Stunden pro Woche  ");

        service.saveAiDetails(details, context.userId(), context.session());

        assertThat(service.requireOwned(context.userId(), context.session()).getAvailableWorkingTime())
                .isEqualTo("5 Stunden pro Woche");
        assertThat(service.aiSummary(context.userId(), context.session()).availableWorkingTime())
                .isEqualTo("5 Stunden pro Woche");
    }

    @Test
    void softwareQuestionsDoNotAcceptRenovationFields() {
        var questions = AiProjectQuestionCatalog.questionsFor(
                ProjectCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT);

        assertThat(questions).extracting("key")
                .contains("goalAndScope", "technologies", "technicalExperience")
                .doesNotContain("technicalConstraints")
                .doesNotContain("affectedRooms", "plannedWork");
        assertThat(AiProjectQuestionCatalog.containsUnknownKey(
                ProjectCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT,
                Map.of("affectedRooms", "Wohnzimmer"))).isTrue();
        assertThat(AiProjectQuestionCatalog.sanitize(
                ProjectCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT,
                Map.of("goalAndScope", "Kleine Webanwendung", "affectedRooms", "Wohnzimmer")))
                .containsOnlyKeys("goalAndScope");
    }

    @Test
    void concreteSoftwareAndTechnologyVariantsOfferOptionalTechnicalExperience() {
        for (var subcategory : ProjectSubCategory.values()) {
            if (subcategory.getCategory() != ProjectCategory.SOFTWARE_TECHNOLOGY
                    || subcategory == ProjectSubCategory.OTHER_SOFTWARE_AND_TECHNOLOGY) {
                continue;
            }
            assertThat(AiProjectQuestionCatalog.questionsFor(
                    ProjectCategory.SOFTWARE_TECHNOLOGY, subcategory))
                    .filteredOn(question -> question.key().equals("technicalExperience"))
                    .singleElement()
                    .satisfies(question -> {
                        assertThat(question.label()).isEqualTo("Technischer Kenntnisstand");
                        assertThat(question.required()).isFalse();
                    });
        }
    }

    @Test
    void educationDoesNotOfferASeparateStudyTimeField() {
        for (var subcategory : java.util.List.of(
                ProjectSubCategory.EXAM_PREPARATION, ProjectSubCategory.LEARNING_PLAN)) {
            assertThat(AiProjectQuestionCatalog.questionsFor(ProjectCategory.EDUCATION, subcategory))
                    .extracting("key")
                    .doesNotContain("availableStudyTime");
        }
    }

    @Test
    void catalogAvoidsDuplicateCatchAllAndTimeBudgetQuestions() {
        for (var subcategory : ProjectSubCategory.values()) {
            var questions = AiProjectQuestionCatalog.questionsFor(
                    subcategory.getCategory(), subcategory);

            assertThat(questions).hasSizeLessThanOrEqualTo(5);
            assertThat(questions).allSatisfy(question -> assertThat(question.required()).isFalse());
            assertThat(questions).extracting("key").doesNotContain(
                    "availableStudyTime", "availableTime", "specialRequirements",
                    "specialConstraints", "technicalConstraints", "userStatedConstraints",
                    "relevantConditions", "conditions");
        }
    }

    @Test
    void consolidatedTechnicalQuestionsKeepRelevantPlanningInformation() {
        var webQuestions = AiProjectQuestionCatalog.questionsFor(
                ProjectCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.WEB_OR_MOBILE_APP);
        assertThat(webQuestions).filteredOn(question -> question.key().equals("technicalRequirements"))
                .singleElement()
                .extracting("label")
                .isEqualTo("Technische Vorgaben, Architektur und Schnittstellen");
        assertThat(webQuestions).extracting("key")
                .doesNotContain("applicationArchitecture", "externalInterfaces");

        assertThat(AiProjectQuestionCatalog.questionsFor(
                ProjectCategory.SOFTWARE_TECHNOLOGY,
                ProjectSubCategory.HARDWARE_OR_RASPBERRY_PI_PROJECT))
                .extracting("key")
                .contains("requiredComponents")
                .doesNotContain("availableHardware");
    }

    @Test
    void otherUsesGenericQuestionsWithoutInventingASubcategory() {
        assertThat(AiProjectQuestionCatalog.questionsFor(ProjectCategory.OTHER, null))
                .extracting("key")
                .containsExactly("desiredOutcome");
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

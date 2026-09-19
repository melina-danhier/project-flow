package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.model.ProjectQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectQuestionCatalogLoaderTest {

    private ProjectQuestionCatalogLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ProjectQuestionCatalogLoader();
    }

    @Test
    @DisplayName("Zeitfrage steht immer am Anfang und Abschlussfrage steht immer zuletzt")
    void timeQuestionIsFirstAndConstraintsQuestionIsLast() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        assertThat(questions).isNotEmpty();
        assertThat(questions.getFirst().key()).isEqualTo("availableTime");
        assertThat(questions.getFirst().label()).contains("Wie viel Zeit");
        assertThat(questions.getLast().key()).isEqualTo("constraints");
        assertThat(questions.getLast().label()).contains("zusätzlich");
    }

    @Test
    @DisplayName("Konkrete Unterkategorie enthält keine Auffangfrage der Oberkategorie")
    void categoryWithConcreteSubcategoryIncludesBoth() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).contains("availableTime", "topic", "constraints");
        assertThat(keys).doesNotContain("educationGoal");
    }

    @Test
    @DisplayName("Sonstige Unterkategorie enthält ihre Auffangfragen")
    void categoryWithOtherSubcategoryIncludesFallbackQuestions() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.OTHER_EDUCATION);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).contains("availableTime", "educationGoal", "constraints");
        assertThat(keys).doesNotContain("topic", "examSubject", "learningGoal");
    }

    @Test
    @DisplayName("Fehlende erforderliche Unterkategorie liefert keine Auffangfragen")
    void categoryWithoutSubcategoryExcludesFallbackQuestions() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, null);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).containsExactly("availableTime", "constraints");
        assertThat(keys).doesNotContain("topic", "examSubject", "learningGoal");
    }

    @Test
    @DisplayName("Placeholder werden korrekt aus YAML geladen")
    void placeholdersAreLoadedCorrectly() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        ProjectQuestion timeQ = questions.getFirst();
        assertThat(timeQ.placeholder()).isNotBlank();
        assertThat(timeQ.placeholder()).startsWith("z. B.");

        ProjectQuestion constraintsQ = questions.getLast();
        assertThat(constraintsQ.placeholder()).isNotBlank();
        assertThat(constraintsQ.placeholder()).startsWith("z. B.");

        ProjectQuestion topicQ = questions.stream()
                .filter(q -> "topic".equals(q.key()))
                .findFirst()
                .orElseThrow();
        assertThat(topicQ.placeholder()).isNotBlank();
        assertThat(topicQ.placeholder()).contains("Künstliche Intelligenz");
    }

    @Test
    @DisplayName("Unbekannte oder null Kategorie wird sicher behandelt")
    void unknownOrNullCategoryHandledGracefully() {
        List<ProjectQuestion> questionsNull = loader.questionsFor(null, null);
        assertThat(questionsNull).hasSize(2);
        assertThat(questionsNull.getFirst().key()).isEqualTo("availableTime");
        assertThat(questionsNull.getLast().key()).isEqualTo("constraints");

        List<ProjectQuestion> questionsOther = loader.questionsFor(ProjectCategory.OTHER, null);
        assertThat(questionsOther).hasSize(2);
        assertThat(questionsOther.getFirst().key()).isEqualTo("availableTime");
        assertThat(questionsOther.getLast().key()).isEqualTo("constraints");
    }

    @Test
    @DisplayName("dynamicQuestionsFor liefert nur die fachlichen Fragen ohne Zeit und Constraints")
    void dynamicQuestionsExcludesCommonQuestions() {
        List<ProjectQuestion> dynamic = loader.dynamicQuestionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        List<String> keys = dynamic.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).doesNotContain("availableTime", "constraints");
        assertThat(keys).contains("topic");
        assertThat(keys).doesNotContain("educationGoal");
    }

    @Test
    @DisplayName("Universelle Oberkategorie-Frage wird genau einmal mit jeder Unterkategorie kombiniert")
    void universalCategoryQuestionIsIncludedWithoutDuplication() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.SOFTWARE_TECHNOLOGY, ProjectSubCategory.SOFTWARE_PROJECT);

        assertThat(questions).extracting(ProjectQuestion::key)
                .contains("technicalExperience", "goalAndScope")
                .doesNotContain("techGoal")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Universelle Veranstaltungsfrage passt auch zu Online-Aktionen")
    void universalEventQuestionIncludesParticipationAndReach() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EVENT, ProjectSubCategory.FUNDRAISING_EVENT);

        assertThat(questions).filteredOn(question -> question.key().equals("expectedParticipants"))
                .singleElement()
                .extracting(ProjectQuestion::label)
                .asString()
                .contains("teilnehmen", "erreicht");
    }

    @Test
    @DisplayName("Präsentationsfragen trennen Vorgaben und gewünschte Bestandteile")
    void presentationQuestionsDoNotRepeatHandout() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        ProjectQuestion requirements = questions.stream()
                .filter(question -> question.key().equals("contentRequirements"))
                .findFirst().orElseThrow();
        ProjectQuestion deliverables = questions.stream()
                .filter(question -> question.key().equals("desiredDeliverables"))
                .findFirst().orElseThrow();

        assertThat(requirements.placeholder()).doesNotContainIgnoringCase("Handout");
        assertThat(deliverables.placeholder()).containsIgnoringCase("Handout");
    }

    @Test
    @DisplayName("Fragetexte vermeiden unnötige Fachbegriffe")
    void questionLabelsAvoidUnexplainedJargon() {
        List<String> labels = java.util.Arrays.stream(ProjectSubCategory.values())
                .flatMap(subcategory -> loader.questionsFor(subcategory.getCategory(), subcategory).stream())
                .map(ProjectQuestion::label)
                .toList();

        assertThat(labels).allSatisfy(label -> assertThat(label)
                .doesNotContain("Methodik", "Codebasis", "technische Abhängigkeiten"));
    }

    @Test
    @DisplayName("Keine auswählbare Unterkategorie erzeugt doppelte Frageschlüssel")
    void everySubcategoryHasUniqueQuestionKeys() {
        for (ProjectSubCategory subcategory : ProjectSubCategory.values()) {
            List<String> keys = loader.questionsFor(subcategory.getCategory(), subcategory).stream()
                    .map(ProjectQuestion::key)
                    .toList();
            assertThat(new HashSet<>(keys))
                    .as("Fragen für %s", subcategory)
                    .hasSameSizeAs(keys);
        }
    }
}

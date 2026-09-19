package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.model.ProjectQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertThat(questions.getLast().label()).contains("besondere");
    }

    @Test
    @DisplayName("Oberkategorie + konkrete Unterkategorie: enthält Kategorie- und Unterkategorie-Fragen")
    void categoryWithConcreteSubcategoryIncludesBoth() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).contains("availableTime", "educationGoal", "topic", "constraints");
    }

    @Test
    @DisplayName("Oberkategorie + OTHER: enthält nur Kategorie-Fragen, keine Unterkategorie-Fragen")
    void categoryWithOtherSubcategoryExcludesSubcategoryQuestions() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, ProjectSubCategory.OTHER_EDUCATION);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).contains("availableTime", "educationGoal", "constraints");
        assertThat(keys).doesNotContain("topic", "examSubject", "learningGoal");
    }

    @Test
    @DisplayName("Oberkategorie ohne Unterkategorie: enthält nur Kategorie-Fragen")
    void categoryWithoutSubcategoryExcludesSubcategoryQuestions() {
        List<ProjectQuestion> questions = loader.questionsFor(
                ProjectCategory.EDUCATION, null);

        List<String> keys = questions.stream().map(ProjectQuestion::key).toList();
        assertThat(keys).contains("availableTime", "educationGoal", "constraints");
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
        assertThat(keys).contains("educationGoal", "topic");
    }
}

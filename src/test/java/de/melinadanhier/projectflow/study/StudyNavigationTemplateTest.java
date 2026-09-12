package de.melinadanhier.projectflow.study;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudyNavigationTemplateTest {

    @Test
    void projectOverviewAndPlanIncludeStudyReturnOptions() throws IOException {
        String overview = Files.readString(Path.of(
                "src/main/resources/templates/projects/overview.html"));
        String plan = Files.readString(Path.of(
                "src/main/resources/templates/projects/plan.html"));
        String layout = Files.readString(Path.of(
                "src/main/resources/templates/fragments/layout.html"));

        assertThat(overview).contains("fragments/layout :: study-return");
        assertThat(plan).contains("fragments/layout :: study-return");
        assertThat(layout)
                .contains("th:fragment=\"study-return\"")
                .contains("session.studyPhase == 'TASK_1'")
                .contains("session.studyPhase == 'TASK_2'")
                .contains("Aufgabe 1 der Nutzerstudie")
                .contains("Du ziehst in sechs Wochen in eine Wohnung in einer anderen Stadt.")
                .contains("Übernimm am Ende einen Projektplan, mit dem du grundsätzlich weiterarbeiten würdest.")
                .contains("Aufgabe 2 der Nutzerstudie")
                .contains("Die Freunde, die dir ursprünglich beim Transport helfen wollten")
                .contains("Prüfe den Änderungsvorschlag und entscheide anschließend selbst")
                .contains("<details class=\"pf-study-task__details\" open>")
                .contains("@{/study/return}")
                .contains("@{/study/finish}");
    }
}

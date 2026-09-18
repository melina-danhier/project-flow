package de.melinadanhier.projectflow.study;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudyNavigationTemplateTest {

    @Test
    void globalHeaderIncludesStudyReturnOptions() throws IOException {
        String overview = Files.readString(Path.of(
                "src/main/resources/templates/projects/overview.html"));
        String plan = Files.readString(Path.of(
                "src/main/resources/templates/projects/plan.html"));
        String layout = Files.readString(Path.of(
                "src/main/resources/templates/fragments/layout.html"));

        assertThat(overview).doesNotContain("fragments/layout :: study-return");
        assertThat(plan).doesNotContain("fragments/layout :: study-return");
        assertThat(layout)
                .contains("th:fragment=\"study-return\"")
                .contains("th:fragment=\"site-header\"")
                .contains("<th:block th:replace=\"~{fragments/layout :: study-return}\" />")
                .contains("session.studyPhase == 'TASK_1'")
                .contains("session.studyPhase == 'TASK_2'")
                .contains("Aufgabe 1 der Nutzerstudie")
                .contains("Du planst einen Umzug in eine neue Wohnung innerhalb derselben Stadt.")
                .contains("Übernimm am Ende einen Projektplan, mit dem du grundsätzlich weiterarbeiten würdest.")
                .contains("Aufgabe 2 der Nutzerstudie")
                .contains("Die Freunde, die dir ursprünglich beim Transport helfen wollten")
                .contains("oben im Plan <strong>„Plan mit KI verbessern“</strong>")
                .contains("<strong>„Aufgabe mit KI anpassen“</strong>")
                .contains("<details class=\"pf-study-task__details\">")
                .doesNotContain("<details class=\"pf-study-task__details\" open>")
                .contains("th:action=\"@{/study/task-2/complete}\"")
                .contains("th:action=\"@{/study/abort}\"")
                .contains("th:action=\"@{/study/task-1/complete}\"")
                .contains("plan != null and plan.project != null and plan.editable")
                .contains("data-confirm=\"Möchtest du Aufgabe 1 wirklich abschließen")
                .contains("data-confirm=\"Möchtest du Aufgabe 2 wirklich abschließen")
                .contains("pf-study-task-intro-dialog")
                .contains("keine echten personenbezogenen");
        assertThat(layout)
                .contains("@{/css/study-mode.css}")
                .contains("@{/js/study-mode.js}");

        String start = Files.readString(Path.of(
                "src/main/resources/templates/study/start.html"));
        assertThat(start)
                .contains("pf-study-info-details")
                .contains("Weitere Studien- und Datenschutzinformationen")
                .contains("ca. 15–20 Minuten")
                .contains("ca. 5–10 Minuten")
                .contains("ca. 20–30 Minuten")
                .contains("melinadanhier@gmail.com");

        String completed = Files.readString(Path.of(
                "src/main/resources/templates/study/completed.html"));
        assertThat(completed)
                .contains("Aufgaben abgeschlossen")
                .contains("Die Aufgaben in ProjectFlow sind abgeschlossen. Im nächsten Schritt wirst du zu einem anonymen Fragebogen weitergeleitet.")
                .contains("Zum Fragebogen")
                .contains("th:action=\"@{/study/finish}\"");

        String planChangeReview = Files.readString(Path.of(
                "src/main/resources/templates/projects/plan-change/review.html"));
        assertThat(planChangeReview)
                .contains("KI-Änderungen prüfen")
                .contains("element.typeLabel == 'Meilenstein'")
                .doesNotContain("th:text=\"${element.typeLabel}\"");

        String draftReview = Files.readString(Path.of(
                "src/main/resources/templates/generation/draft-review.html"));
        assertThat(draftReview)
                .contains("pf-back-to-top-wrap")
                .contains("Nach oben");
    }
}

package de.melinadanhier.projectflow.planelement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiActionPlacementTemplateTest {

    @Test
    void planAiActionIsProminentAndNoLongerInsideProjectMenu() throws IOException {
        String plan = Files.readString(Path.of(
                "src/main/resources/templates/projects/plan.html"));

        int aiAction = plan.indexOf("<span>Plan mit KI verbessern</span>");
        int projectMenu = plan.indexOf("id=\"project-actions-dropdown\"");

        assertThat(aiAction).isGreaterThanOrEqualTo(0).isLessThan(projectMenu);
        assertThat(plan).doesNotContain("<span class=\"pf-dropdown__eyebrow\">KI-Funktionen</span>");
    }

    @Test
    void taskAiActionPlacementMatchesDesktopAndMobile() throws IOException {
        String detail = Files.readString(Path.of(
                "src/main/resources/templates/projects/tasks/detail.html"));

        assertThat(detail).contains("pf-task-header-actions--desktop");
        assertThat(detail).contains("pf-task-header-actions--mobile");
        assertThat(detail).contains("Mit KI anpassen");
        assertThat(detail).contains("<span>Löschen</span>");
        assertThat(detail).contains("pf-task-prerequisites-section");
        assertThat(detail.indexOf("Mit KI anpassen"))
                .isLessThan(detail.indexOf("<span class=\"pf-detail-prop__label\">Beschreibung</span>"));
    }
}

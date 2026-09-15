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
    void taskAiActionRemainsInMenuAndAppearsBelowProperties() throws IOException {
        String detail = Files.readString(Path.of(
                "src/main/resources/templates/projects/tasks/detail.html"));

        assertThat(detail).contains("<span>Mit KI verbessern</span>");
        assertThat(detail).contains("Aufgabe mit KI anpassen");
        assertThat(detail.indexOf("Aufgabe mit KI anpassen"))
                .isGreaterThan(detail.indexOf("<span class=\"pf-detail-prop__label\">Herkunft</span>"));
    }
}

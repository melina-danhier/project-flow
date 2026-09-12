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
                .contains("@{/study/return}")
                .contains("@{/study/finish}");
    }
}

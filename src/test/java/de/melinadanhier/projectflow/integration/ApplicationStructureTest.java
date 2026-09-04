package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStructureTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path JAVA_ROOT = PROJECT_ROOT.resolve("src/main/java/de/melinadanhier/projectflow");
    private static final Path TEMPLATE_ROOT = PROJECT_ROOT.resolve("src/main/resources/templates");

    @Test
    void featureAreasDoNotDependOnWizardInternals() throws IOException {
        for (String feature : Set.of("plancontainer/project", "plancontainer/template", "generation")) {
            try (var sources = Files.walk(JAVA_ROOT.resolve(feature))) {
                assertThat(sources.filter(path -> path.toString().endsWith(".java")))
                        .allSatisfy(path -> assertThat(read(path))
                                .doesNotContain("de.melinadanhier.projectflow.wizard"));
            }
        }
    }

    @Test
    void aiWorkflowRedirectTargetsRenderSuccessMessages() {
        for (String template : Set.of(
                "generation/ai-summary.html",
                "generation/ai-status.html",
                "generation/ai-problems.html"
        )) {
            assertThat(read(TEMPLATE_ROOT.resolve(template)))
                    .contains("th:if=\"${successMessage}\"")
                    .contains("th:text=\"${successMessage}\"")
                    .contains("role=\"status\"");
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Quelldatei konnte nicht gelesen werden: " + path, exception);
        }
    }
}

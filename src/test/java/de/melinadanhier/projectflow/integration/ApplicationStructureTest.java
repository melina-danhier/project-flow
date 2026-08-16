package de.melinadanhier.projectflow.integration;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.generation.model.PlanDraft;
import de.melinadanhier.projectflow.plancontainer.model.PlanContainer;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.user.model.User;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStructureTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path JAVA_ROOT = PROJECT_ROOT.resolve("src/main/java/de/melinadanhier/projectflow");
    private static final Path RESOURCE_ROOT = PROJECT_ROOT.resolve("src/main/resources");
    private static final Path TEST_ROOT = PROJECT_ROOT.resolve("src/test/java/de/melinadanhier/projectflow");

    @Test
    void placesCoreModelTypesInTheirFeaturePackages() {
        assertThat(User.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.user.model");
        assertThat(PlanContainer.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.plancontainer.model");
        assertThat(Project.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.plancontainer.project.model");
        assertThat(Template.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.plancontainer.template.model");
        assertThat(PlanElement.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.planelement.model");
        assertThat(PlanSection.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.planelement.model");
        assertThat(PlanDraft.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.generation.model");
        assertThat(MutableEntity.class.getPackageName()).isEqualTo("de.melinadanhier.projectflow.common.model");
    }

    @Test
    void productionPackagesMatchTheRequiredDirectoryTreeExactly() throws IOException {
        assertThat(JAVA_ROOT.resolve("ProjectFlowApplication.java")).isRegularFile();
        assertThat(relativeDirectories(JAVA_ROOT)).containsExactlyInAnyOrderElementsOf(Set.of(
                "user", "user/controller", "user/dto", "user/mapper", "user/model", "user/repository", "user/service",
                "plancontainer", "plancontainer/model",
                "plancontainer/project", "plancontainer/project/controller", "plancontainer/project/dto",
                "plancontainer/project/mapper", "plancontainer/project/model", "plancontainer/project/repository",
                "plancontainer/project/service",
                "plancontainer/template", "plancontainer/template/controller", "plancontainer/template/dto",
                "plancontainer/template/mapper", "plancontainer/template/model", "plancontainer/template/repository",
                "plancontainer/template/service",
                "planelement", "planelement/controller", "planelement/dto", "planelement/mapper", "planelement/model",
                "planelement/repository", "planelement/service", "planelement/validation",
                "generation", "generation/client", "generation/controller", "generation/dto", "generation/dto/request",
                "generation/dto/response", "generation/mapper", "generation/model", "generation/parser",
                "generation/prompt", "generation/repository", "generation/service", "generation/validation",
                "security", "security/config", "security/handler", "security/service", "security/validation",
                "common", "common/config", "common/exception", "common/model", "common/util"
        ));
    }

    @Test
    void resourcesAndTestsMatchTheRequiredDirectoryTreesExactly() throws IOException {
        assertThat(relativeDirectories(RESOURCE_ROOT)).containsExactlyInAnyOrderElementsOf(Set.of(
                "ai", "ai/prompts", "ai/schema", "db", "db/migration", "static", "static/css", "static/images",
                "static/js", "templates", "templates/auth", "templates/error", "templates/fragments",
                "templates/generation", "templates/projects", "templates/projects/tasks",
                "templates/projects/milestones", "templates/templates"
        ));
        assertThat(RESOURCE_ROOT.resolve("application.yml")).isRegularFile();
        assertThat(RESOURCE_ROOT.resolve("application-dev.yml")).isRegularFile();
        assertThat(RESOURCE_ROOT.resolve("application-prod.yml")).isRegularFile();
        assertThat(RESOURCE_ROOT.resolve("application-test.yml")).isRegularFile();

        assertThat(relativeDirectories(TEST_ROOT)).containsExactlyInAnyOrderElementsOf(Set.of(
                "user", "plancontainer", "plancontainer/project", "plancontainer/template", "planelement",
                "generation", "security", "integration"
        ));
    }

    @Test
    void noProductiveSourceUsesTheFormerBasePackage() throws IOException {
        try (var sources = Files.walk(PROJECT_ROOT.resolve("src/main/java"))) {
            assertThat(sources.filter(path -> path.toString().endsWith(".java")))
                    .allSatisfy(path -> assertThat(read(path)).doesNotContain("com.melina.projectflow"));
        }
        assertThat(PROJECT_ROOT.resolve("src/main/java/com/melina/projectflow")).doesNotExist();
    }

    private Set<String> relativeDirectories(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .map(root::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
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

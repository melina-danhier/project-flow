package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.model.AiProjectQuestion;
import de.melinadanhier.projectflow.wizard.model.AiQuestionType;
import de.melinadanhier.projectflow.wizard.model.ProjectQuestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central, UI-independent definition of the additional AI wizard input.
 * Delegates to {@link ProjectQuestionCatalogLoader} for YAML-based definitions.
 */
public final class AiProjectQuestionCatalog {

    private static final int TEXT_LIMIT = 1000;
    private static volatile ProjectQuestionCatalogLoader loader;

    private AiProjectQuestionCatalog() { }

    /**
     * Inject the loader from Spring context.  Called by the loader itself
     * after construction.  This bridge allows static access while the
     * actual data lives in the Spring-managed loader.
     */
    public static void init(ProjectQuestionCatalogLoader catalogLoader) {
        loader = catalogLoader;
    }

    public static List<AiProjectQuestion> questionsFor(
            ProjectCategory category, ProjectSubCategory subcategory) {
        if (loader == null) {
            return List.of(toAi(new ProjectQuestion("desiredOutcome", "Konkretes Ziel oder gewünschtes Ergebnis", null)));
        }
        return loader.dynamicQuestionsFor(category, subcategory).stream()
                .map(AiProjectQuestionCatalog::toAi)
                .toList();
    }

    public static Map<String, String> sanitize(
            ProjectCategory category, ProjectSubCategory subcategory, Map<String, String> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return Map.of();
        }
        Set<String> allowed = questionsFor(category, subcategory).stream()
                .map(AiProjectQuestion::key).collect(Collectors.toSet());
        Map<String, String> result = new LinkedHashMap<>();
        submitted.forEach((key, value) -> {
            if (allowed.contains(key) && value != null && !value.isBlank()) {
                result.put(key, value.trim());
            }
        });
        return Map.copyOf(result);
    }

    public static boolean containsUnknownKey(
            ProjectCategory category, ProjectSubCategory subcategory, Map<String, String> submitted) {
        if (submitted == null) {
            return false;
        }
        Set<String> allowed = questionsFor(category, subcategory).stream()
                .map(AiProjectQuestion::key).collect(Collectors.toSet());
        return submitted.keySet().stream().anyMatch(key -> !allowed.contains(key));
    }

    private static AiProjectQuestion toAi(ProjectQuestion q) {
        return new AiProjectQuestion(q.key(), q.label(), q.placeholder(), AiQuestionType.TEXTAREA, false, TEXT_LIMIT);
    }
}

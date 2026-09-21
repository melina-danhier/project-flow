package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.model.ProjectQuestion;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Loads the project question catalog from {@code project-question-catalog.yml}
 * on the classpath.  Thread-safe and immutable after construction.
 */
@Component
public class ProjectQuestionCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(ProjectQuestionCatalogLoader.class);

    private final ProjectQuestion availableTime;
    private final ProjectQuestion finalConstraints;
    private final Map<ProjectCategory, List<ProjectQuestion>> categoryQuestions;
    private final Map<ProjectSubCategory, List<ProjectQuestion>> subcategoryQuestions;

    @PostConstruct
    void registerWithStaticCatalog() {
        AiProjectQuestionCatalog.init(this);
    }

    @SuppressWarnings("unchecked")
    public ProjectQuestionCatalogLoader() {
        Map<ProjectCategory, List<ProjectQuestion>> catQ = new LinkedHashMap<>();
        Map<ProjectSubCategory, List<ProjectQuestion>> subQ = new LinkedHashMap<>();
        ProjectQuestion time = null;
        ProjectQuestion constraints = null;

        try {
            Yaml yaml = new Yaml();
            InputStream is = new ClassPathResource("project-question-catalog.yml").getInputStream();
            Map<String, Object> root = yaml.load(is);

            // Parse common questions
            Map<String, Object> common = (Map<String, Object>) root.get("common");
            if (common != null) {
                time = parseQuestion((Map<String, Object>) common.get("availableTime"));
                constraints = parseQuestion((Map<String, Object>) common.get("finalConstraints"));
            }

            // Parse categories
            Map<String, Object> categories = (Map<String, Object>) root.get("categories");
            if (categories != null) {
                for (var catEntry : categories.entrySet()) {
                    String catName = catEntry.getKey();
                    ProjectCategory category = safeParseCat(catName);
                    if (category == null) {
                        log.warn("Unknown ProjectCategory in YAML: {}", catName);
                        continue;
                    }

                    Map<String, Object> catData = (Map<String, Object>) catEntry.getValue();
                    List<Map<String, Object>> questionsList = (List<Map<String, Object>>) catData.get("questions");
                    if (questionsList != null) {
                        catQ.put(category, questionsList.stream().map(this::parseQuestion).filter(Objects::nonNull).toList());
                    }

                    // Parse subcategories
                    Map<String, Object> subs = (Map<String, Object>) catData.get("subcategories");
                    if (subs != null) {
                        for (var subEntry : subs.entrySet()) {
                            String subName = subEntry.getKey();
                            ProjectSubCategory sub = safeParseSubCat(subName);
                            if (sub == null) {
                                log.warn("Unknown ProjectSubCategory in YAML: {}", subName);
                                continue;
                            }
                            if (sub.getCategory() != category) {
                                log.warn("ProjectSubCategory {} is listed below the wrong category {}", subName, catName);
                                continue;
                            }
                            Map<String, Object> subData = (Map<String, Object>) subEntry.getValue();
                            List<Map<String, Object>> subQuestions = (List<Map<String, Object>>) subData.get("questions");
                            if (subQuestions != null) {
                                subQ.put(sub, subQuestions.stream().map(this::parseQuestion).filter(Objects::nonNull).toList());
                            }
                        }
                    }
                }
            }

            log.info("Loaded question catalog: {} categories, {} subcategories", catQ.size(), subQ.size());
        } catch (Exception e) {
            log.error("Failed to load project-question-catalog.yml, using empty catalog", e);
        }

        this.availableTime = time != null ? time :
                new ProjectQuestion("availableTime", "Wie viel Zeit kannst du ungefähr für das Projekt aufbringen?", null);
        this.finalConstraints = constraints != null ? constraints :
                new ProjectQuestion("constraints", "Welche besonderen Wünsche, Vorgaben oder Einschränkungen sollen berücksichtigt werden?", null);
        this.categoryQuestions = Map.copyOf(catQ);
        this.subcategoryQuestions = Map.copyOf(subQ);
    }

    /**
     * Returns the assembled, ordered list of all questions for the given category/subcategory.
     * Order: availableTime -> category questions -> subcategory questions -> finalConstraints.
     * Always puts availableTime first and finalConstraints last.
     */
    public List<ProjectQuestion> questionsFor(ProjectCategory category, ProjectSubCategory subcategory) {
        List<ProjectQuestion> result = new ArrayList<>();
        result.add(availableTime);

        result.addAll(dynamicQuestionsFor(category, subcategory));

        result.add(finalConstraints);
        return List.copyOf(result);
    }

    /**
     * Returns only the category- and subcategory-specific questions (without common questions).
     * Used by the wizard form to render the dynamic middle section.
     */
    public List<ProjectQuestion> dynamicQuestionsFor(ProjectCategory category, ProjectSubCategory subcategory) {
        List<ProjectQuestion> result = new ArrayList<>();

        if (category != null) {
            List<ProjectQuestion> catQuestions = categoryQuestions.getOrDefault(category, List.of());
            if (subcategory == ProjectSubCategory.MOVING) {
                catQuestions = catQuestions.stream()
                        .filter(q -> !"homeGoal".equals(q.key()))
                        .toList();
            }
            result.addAll(catQuestions);

            if (subcategory != null && subcategory.getCategory() == category) {
                result.addAll(subcategoryQuestions.getOrDefault(subcategory, List.of()));
            }
        }

        return List.copyOf(result);
    }

    /** Returns all valid question keys for the given category/subcategory. */
    public Set<String> allowedKeys(ProjectCategory category, ProjectSubCategory subcategory) {
        Set<String> keys = new LinkedHashSet<>();
        questionsFor(category, subcategory).forEach(q -> keys.add(q.key()));
        return keys;
    }

    public ProjectQuestion getAvailableTime() {
        return availableTime;
    }

    public ProjectQuestion getFinalConstraints() {
        return finalConstraints;
    }

    private ProjectQuestion parseQuestion(Map<String, Object> map) {
        if (map == null) return null;
        String key = (String) map.get("key");
        String label = (String) map.get("label");
        String placeholder = (String) map.get("placeholder");
        if (key == null || label == null) {
            log.warn("Skipping question with missing key or label: {}", map);
            return null;
        }
        return new ProjectQuestion(key, label, placeholder);
    }

    private ProjectCategory safeParseCat(String name) {
        try {
            return ProjectCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ProjectSubCategory safeParseSubCat(String name) {
        try {
            return ProjectSubCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

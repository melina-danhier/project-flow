package de.melinadanhier.projectflow.ai.model;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.improvement.AiTextImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskReplanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiMilestoneReplanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskEffortResponse;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;
import static java.util.Map.entry;

/** JSON-Schemata der gemeinsamen DTOs; Versionierung erfolgt über AiSchemaVersions im Backend. */
public final class AiResponseSchemas {
    private AiResponseSchemas() {
    }

    public static Map<String, Object> forType(Class<?> type) {
        if (type == AiPreCheckResult.class) {
            return preCheckSchema();
        }
        if (type == GeneratedPlanResponse.class) {
            return generatedPlanSchema();
        }
        if (type == AiTextImprovementResponse.class) {
            return textImprovementSchema();
        }
        if (type == AiTaskReplanResponse.class) {
            return object(Map.ofEntries(
                    entry("startDate", nullable(date())),
                    entry("dueDate", nullable(date())),
                    entry("placement", replanPlacementSchema()),
                    entry("explanation", boundedString(500))));
        }
        if (type == AiMilestoneReplanResponse.class) {
            return object(Map.ofEntries(
                    entry("dueDate", nullable(date())),
                    entry("placement", replanPlacementSchema()),
                    entry("explanation", boundedString(500))));
        }
        if (type == AiTaskEffortResponse.class) {
            return object(Map.ofEntries(
                    entry("estimatedHours", positiveInteger(MAX_ESTIMATED_HOURS)),
                    entry("explanation", boundedString(500))));
        }
        throw new IllegalArgumentException("Kein KI-Ausgabeschema für " + type.getName());
    }

    private static Map<String, Object> textImprovementSchema() {
        return object(Map.ofEntries(
                entry("title", string()),
                entry("description", nullable(string()))
        ));
    }

    private static Map<String, Object> replanPlacementSchema() {
        return object(Map.ofEntries(
                entry("changePlacement", Map.of("type", "boolean")),
                entry("targetSectionId", nullable(string())),
                entry("beforeElementId", nullable(string())),
                entry("afterElementId", nullable(string()))
        ));
    }

    private static Map<String, Object> preCheckSchema() {
        return object(Map.of(
                "problems",
                array(preCheckProblemSchema(), 0, MAX_PRE_CHECK_PROBLEMS)
        ));
    }

    private static Map<String, Object> preCheckProblemSchema() {
        return object(Map.ofEntries(
                entry("severity", enumeration(AiPreCheckSeverity.class)),
                entry("type", enumeration(AiPreCheckProblemType.class)),
                entry("message", string()),
                entry("suggestedUserAction", string()),
                entry("reviewQuestion", string()),
                entry("acceptedInterpretation", string())
        ));
    }

    private static Map<String, Object> generatedPlanSchema() {
        return object(Map.ofEntries(
                entry("sections", array(sectionSchema(), MIN_SECTIONS, MAX_SECTIONS))
        ));
    }

    private static Map<String, Object> sectionSchema() {
        return object(Map.ofEntries(
                entry("tempId", nullable(string())),
                entry("title", string()),
                entry("description", nullable(string())),
                entry("order", positiveInteger()),
                entry("tasks", array(taskSchema(), 1, MAX_TASKS)),
                entry("milestones", array(milestoneSchema(), 0, MAX_MILESTONES))
        ));
    }

    private static Map<String, Object> taskSchema() {
        return object(Map.ofEntries(
                entry("tempId", string()),
                entry("title", string()),
                entry("description", nullable(string())),
                entry("estimatedHours", nullable(positiveInteger(MAX_ESTIMATED_HOURS))),
                entry("startDate", nullable(date())),
                entry("dueDate", nullable(date())),
                entry("origin", enumeration(GeneratedElementOrigin.class)),
                entry("order", positiveInteger()), // Gemeinsamer Nummernkreis innerhalb der Section (z. B. 100, 200...)
                entry("prerequisiteTaskTempIds", array(string(), 0, MAX_DEPENDENCIES)),
                entry("priority", nullable(enumeration(TaskPriority.class)))
        ));
    }

    private static Map<String, Object> milestoneSchema() {
        return object(Map.ofEntries(
                entry("tempId", nullable(string())),
                entry("title", string()),
                entry("date", nullable(date())),
                entry("order", positiveInteger()) // Gemeinsamer Nummernkreis innerhalb der Section (z. B. 300...)
        ));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", properties.keySet().stream().sorted().toList(),
                "additionalProperties", false
        );
    }

    private static Map<String, Object> array(Map<String, Object> items, int min, int max) {
        return Map.of(
                "type", "array",
                "items", items,
                "minItems", min,
                "maxItems", max
        );
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> boundedString(int maxLength) {
        return Map.of("type", "string", "minLength", 1, "maxLength", maxLength);
    }

    private static Map<String, Object> date() {
        return Map.of("type", "string", "format", "date");
    }

    private static Map<String, Object> positiveInteger() {
        return Map.of("type", "integer", "minimum", 1);
    }

    private static Map<String, Object> positiveInteger(int max) {
        return Map.of("type", "integer", "minimum", 1, "maximum", max);
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
    }

    private static Map<String, Object> enumeration(Class<? extends Enum<?>> type) {
        List<String> values = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return Map.of("type", "string", "enum", values);
    }
}

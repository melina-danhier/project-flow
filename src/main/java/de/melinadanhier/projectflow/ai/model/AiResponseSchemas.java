package de.melinadanhier.projectflow.ai.model;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
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
        throw new IllegalArgumentException("Kein KI-Ausgabeschema für " + type.getName());
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
                entry("message", string()),
                entry("suggestion", string())
        ));
    }

    private static Map<String, Object> generatedPlanSchema() {
        return object(Map.of(
                "phases", array(phaseSchema(), MIN_PHASES, MAX_PHASES)
        ));
    }

    private static Map<String, Object> phaseSchema() {
        return object(Map.ofEntries(
                entry("tempId", nullable(string())),
                entry("title", string()),
                entry("description", nullable(string())),
                entry("startDate", nullable(date())),
                entry("endDate", nullable(date())),
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
                entry("criticalAssumption", nullable(string())),
                entry("origin", enumeration(GeneratedElementOrigin.class)),
                entry("order", positiveInteger()),
                entry("prerequisiteTaskTempIds", array(string(), 0, MAX_DEPENDENCIES)),
                entry("priority", nullable(enumeration(TaskPriority.class)))
        ));
    }

    private static Map<String, Object> milestoneSchema() {
        return object(Map.ofEntries(
                entry("tempId", nullable(string())),
                entry("title", string()),
                entry("date", nullable(date())),
                entry("order", positiveInteger())
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

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

/** JSON-Schemata der gemeinsamen DTOs; Versionierung erfolgt über AiSchemaVersions im Backend. */
public final class AiResponseSchemas {
    private AiResponseSchemas() { }

    public static Map<String, Object> forType(Class<?> type) {
        if (type == AiPreCheckResult.class) {
            return object(Map.of("problems", array(object(Map.of(
                    "severity", enumeration(AiPreCheckSeverity.class),
                    "message", string(), "suggestion", string()),
                    "severity", "message", "suggestion"), 0, MAX_PRE_CHECK_PROBLEMS)), "problems");
        }
        if (type == GeneratedPlanResponse.class) {
            var task = object(Map.ofEntries(
                    Map.entry("tempId", string()), Map.entry("title", string()),
                    Map.entry("description", nullable(string())),
                    Map.entry("estimatedHours", nullable(Map.of("type", "integer", "minimum", 1, "maximum", MAX_ESTIMATED_HOURS))),
                    Map.entry("startDate", date()), Map.entry("dueDate", date()),
                    Map.entry("criticalAssumption", nullable(string())),
                    Map.entry("origin", enumeration(GeneratedElementOrigin.class)),
                    Map.entry("order", Map.of("type", "integer", "minimum", 1)),
                    Map.entry("prerequisiteTaskTempIds", array(string(), 0, MAX_DEPENDENCIES)),
                    Map.entry("priority", nullable(enumeration(TaskPriority.class)))),
                    "tempId", "title", "origin", "order", "prerequisiteTaskTempIds");
            var milestone = object(Map.of(
                    "tempId", nullable(string()), "title", string(), "date", date(),
                    "order", Map.of("type", "integer", "minimum", 1)), "title", "order");
            var phase = object(Map.of(
                    "tempId", nullable(string()), "title", string(), "description", nullable(string()),
                    "startDate", date(), "endDate", date(),
                    "order", Map.of("type", "integer", "minimum", 1),
                    "tasks", array(task, 1, MAX_TASKS),
                    "milestones", array(milestone, 0, MAX_MILESTONES)), "title", "order", "tasks", "milestones");
            return object(Map.of("phases", array(phase, MIN_PHASES, MAX_PHASES)), "phases");
        }
        throw new IllegalArgumentException("Kein KI-Ausgabeschema für " + type.getName());
    }

    private static Map<String, Object> object(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties,
                "required", List.of(required), "additionalProperties", false);
    }

    private static Map<String, Object> array(Map<String, Object> items, int min, int max) {
        return Map.of("type", "array", "items", items, "minItems", min, "maxItems", max);
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> date() {
        return nullable(Map.of("type", "string", "format", "date"));
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
    }

    private static Map<String, Object> enumeration(Class<? extends Enum<?>> type) {
        return Map.of("type", "string", "enum", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }
}

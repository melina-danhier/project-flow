package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseSchemasTest {
    @Test
    void schemasMatchDtoFieldsTypesNullabilityAndEnumValuesRecursively() {
        assertSchema(AiResponseSchemas.forType(AiPreCheckResult.class), AiPreCheckResult.class);
        assertSchema(AiResponseSchemas.forType(GeneratedPlanResponse.class), GeneratedPlanResponse.class);
    }

    @Test
    void rejectsUnsupportedResponseTypes() {
        assertThatThrownBy(() -> AiResponseSchemas.forType(String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedPlanUsesSectionsWithoutSectionDates() {
        var planProperties = (Map<String, Object>) AiResponseSchemas.forType(GeneratedPlanResponse.class)
                .get("properties");
        assertThat(planProperties).containsOnlyKeys("sections", "criticalAssumptions");
        var sections = (Map<String, Object>) planProperties.get("sections");
        var section = (Map<String, Object>) sections.get("items");
        var sectionProperties = (Map<String, Object>) section.get("properties");
        assertThat(sectionProperties).containsKeys("title", "description", "order", "tasks", "milestones")
                .doesNotContainKeys("startDate", "endDate");
    }

    @SuppressWarnings("unchecked")
    private void assertSchema(Map<String, Object> schema, Type type) {
        if (schema.containsKey("anyOf")) {
            var alternatives = (List<Map<String, Object>>) schema.get("anyOf");
            assertThat(alternatives).hasSize(2);
            assertThat(alternatives).contains(Map.of("type", "null"));
            schema = alternatives.stream().filter(value -> !"null".equals(value.get("type")))
                    .findFirst().orElseThrow();
        }
        if (type instanceof ParameterizedType listType) {
            assertThat(schema.get("type")).isEqualTo("array");
            assertSchema((Map<String, Object>) schema.get("items"), listType.getActualTypeArguments()[0]);
        } else if (type instanceof Class<?> clazz && clazz.isRecord()) {
            assertThat(schema.get("type")).isEqualTo("object");
            var properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(
                    Arrays.stream(clazz.getRecordComponents()).map(component -> component.getName()).toList());
            assertThat(schema.get("additionalProperties")).isEqualTo(false);
            assertThat((List<String>) schema.get("required"))
                    .as("Every JSON field of %s must be required, including nullable fields", clazz.getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(properties.keySet());
            for (var component : clazz.getRecordComponents()) {
                var fieldSchema = (Map<String, Object>) properties.get(component.getName());
                // Bean Validation annotations on record components are propagated to their accessors.
                boolean permitsNull = !component.getType().isPrimitive()
                        && !component.getAccessor().isAnnotationPresent(NotNull.class)
                        && !component.getAccessor().isAnnotationPresent(NotBlank.class);
                assertThat(fieldSchema.containsKey("anyOf"))
                        .as("Nullability of %s.%s", clazz.getSimpleName(), component.getName())
                        .isEqualTo(permitsNull);
                assertSchema(fieldSchema, component.getGenericType());
            }
        } else if (type instanceof Class<?> clazz && clazz.isEnum()) {
            assertThat(schema.get("type")).isEqualTo("string");
            assertThat((List<String>) schema.get("enum")).containsExactlyInAnyOrderElementsOf(
                    Arrays.stream(clazz.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
        } else if (type == String.class) {
            assertThat(schema.get("type")).isEqualTo("string");
        } else if (type == LocalDate.class) {
            assertThat(schema.get("type")).isEqualTo("string");
            assertThat(schema.get("format")).isEqualTo("date");
        } else if (type == Integer.class || type == int.class) {
            assertThat(schema.get("type")).isEqualTo("integer");
        } else if (type == boolean.class || type == Boolean.class) {
            assertThat(schema.get("type")).isEqualTo("boolean");
        } else {
            throw new AssertionError("Schema test must support DTO type: " + type);
        }
    }
}

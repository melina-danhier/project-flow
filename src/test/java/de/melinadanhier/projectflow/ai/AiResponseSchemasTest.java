package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseSchemasTest {
    @Test
    void schemasCoverExactlyTheSharedDtoFieldsAndEnumValues() {
        assertSchema(AiResponseSchemas.forType(AiPreCheckResult.class), AiPreCheckResult.class);
        assertSchema(AiResponseSchemas.forType(GeneratedPlanResponse.class), GeneratedPlanResponse.class);
    }

    @SuppressWarnings("unchecked")
    private void assertSchema(Map<String, Object> schema, Type type) {
        if (schema.containsKey("anyOf")) {
            schema = ((List<Map<String, Object>>) schema.get("anyOf")).getFirst();
        }
        if (type instanceof ParameterizedType listType) {
            assertThat(schema.get("type")).isEqualTo("array");
            assertSchema((Map<String, Object>) schema.get("items"), listType.getActualTypeArguments()[0]);
        } else if (type instanceof Class<?> clazz && clazz.isRecord()) {
            var properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(
                    Arrays.stream(clazz.getRecordComponents()).map(component -> component.getName()).toList());
            assertThat(schema.get("additionalProperties")).isEqualTo(false);
            for (var component : clazz.getRecordComponents()) {
                assertSchema((Map<String, Object>) properties.get(component.getName()), component.getGenericType());
            }
        } else if (type instanceof Class<?> clazz && clazz.isEnum()) {
            assertThat((List<String>) schema.get("enum")).containsExactlyElementsOf(
                    Arrays.stream(clazz.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
        }
    }
}

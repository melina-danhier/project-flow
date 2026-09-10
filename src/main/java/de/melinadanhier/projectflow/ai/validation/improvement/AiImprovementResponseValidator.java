package de.melinadanhier.projectflow.ai.validation.improvement;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_DESCRIPTION_LENGTH;
import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_ESTIMATED_HOURS;
import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_TITLE_LENGTH;

@Component
public class AiImprovementResponseValidator {

    public static final int MAX_EXPLANATION_LENGTH = 500;

    public AiImprovementContent validate(
            AiImprovementElementType expectedType,
            AiImprovementContent original,
            AiFeedbackType action,
            String comment,
            AiImprovementResponse response
    ) {
        if (original == null || original.elementType() != expectedType) {
            throw invalid(List.of("IMPROVEMENT_ORIGINAL | $ | Die ursprünglichen Elementdaten fehlen."));
        }
        if (action == null || !action.supports(expectedType)) {
            throw invalid(List.of("IMPROVEMENT_ACTION | $.action | Die Aktion ist für den Elementtyp nicht erlaubt."));
        }
        AiImprovementContent proposed = validate(expectedType, response);
        List<String> issues = new ArrayList<>();
        validateExplanation(action, response);
        validatePlacement(action, response, issues);
        switch (action) {
            case IMPROVE, EXPAND, SIMPLIFY -> {
                unchanged(issues, "priority", original.priority(), proposed.priority());
                unchanged(issues, "estimatedHours", original.estimatedHours(), proposed.estimatedHours());
                unchanged(issues, "startDate", original.startDate(), proposed.startDate());
                unchanged(issues, "dueDate", original.dueDate(), proposed.dueDate());
                if (action == AiFeedbackType.IMPROVE && !approximatelySameTextLength(original, proposed)) {
                    issues.add("IMPROVEMENT_SCOPE | $ | IMPROVE muss den ungefähren Textumfang beibehalten.");
                }
            }
            case REPLAN -> {
                unchanged(issues, "title", original.title(), proposed.title());
                unchanged(issues, "description", original.description(), proposed.description());
                unchanged(issues, "priority", original.priority(), proposed.priority());
                unchanged(issues, "estimatedHours", original.estimatedHours(), proposed.estimatedHours());
                if (expectedType == AiImprovementElementType.MILESTONE) {
                    unchanged(issues, "startDate", original.startDate(), proposed.startDate());
                }
            }
            case ESTIMATE_EFFORT -> {
                unchanged(issues, "title", original.title(), proposed.title());
                unchanged(issues, "description", original.description(), proposed.description());
                unchanged(issues, "priority", original.priority(), proposed.priority());
                unchanged(issues, "startDate", original.startDate(), proposed.startDate());
                unchanged(issues, "dueDate", original.dueDate(), proposed.dueDate());
                if (proposed.estimatedHours() == null) {
                    issues.add("IMPROVEMENT_EFFORT | $.estimatedHours | Die Aufwandsschätzung fehlt.");
                }
            }
        }
        if (!issues.isEmpty()) throw invalid(issues);
        return proposed;
    }

    public String validateExplanation(AiFeedbackType action, AiImprovementResponse response) {
        if (response == null) throw invalid(List.of("IMPROVEMENT_EMPTY | $ | Die KI-Antwort fehlt."));
        String explanation = normalizeOptional(response.explanation());
        boolean required = action == AiFeedbackType.REPLAN || action == AiFeedbackType.ESTIMATE_EFFORT;
        if (required && explanation == null) {
            throw invalid(List.of("IMPROVEMENT_EXPLANATION | $.explanation | Die Begründung fehlt."));
        }
        if (!required && explanation != null) {
            throw invalid(List.of("IMPROVEMENT_EXPLANATION | $.explanation | Für diese Aktion ist keine Begründung erlaubt."));
        }
        if (explanation != null && explanation.length() > MAX_EXPLANATION_LENGTH) {
            throw invalid(List.of("IMPROVEMENT_EXPLANATION | $.explanation | Die Begründung ist zu lang."));
        }
        return explanation;
    }

    private void validatePlacement(
            AiFeedbackType action, AiImprovementResponse response, List<String> issues) {
        AiReplanPlacementResponse placement = response.placement();
        if (action != AiFeedbackType.REPLAN) {
            if (placement != null) {
                issues.add("IMPROVEMENT_PLACEMENT | $.placement | Eine Platzierung ist nur für REPLAN erlaubt.");
            }
            return;
        }
        if (placement == null) {
            issues.add("IMPROVEMENT_PLACEMENT | $.placement | Die Platzierungsangabe fehlt.");
            return;
        }
        if (!placement.changePlacement()) {
            if (placement.targetSectionId() != null || placement.beforeElementId() != null
                    || placement.afterElementId() != null) {
                issues.add("IMPROVEMENT_PLACEMENT | $.placement | Bei unveränderter Platzierung müssen alle IDs leer sein.");
            }
            return;
        }
        if (placement.beforeElementId() != null && placement.afterElementId() != null) {
            issues.add("IMPROVEMENT_PLACEMENT | $.placement | Es ist nur beforeElementId oder afterElementId erlaubt.");
        }
        rejectBlankId(issues, "targetSectionId", placement.targetSectionId());
        rejectBlankId(issues, "beforeElementId", placement.beforeElementId());
        rejectBlankId(issues, "afterElementId", placement.afterElementId());
    }

    private void rejectBlankId(List<String> issues, String field, String value) {
        if (value != null && value.isBlank()) {
            issues.add("IMPROVEMENT_PLACEMENT | $.placement." + field + " | Eine leere ID ist ungültig.");
        }
    }

    public AiImprovementContent validate(AiImprovementElementType expectedType, AiImprovementResponse response) {
        List<String> issues = new ArrayList<>();
        if (response == null) throw invalid(List.of("IMPROVEMENT_EMPTY | $ | Die KI-Antwort fehlt."));
        if (response.elementType() != expectedType) {
            issues.add("IMPROVEMENT_TYPE | $.elementType | Der Elementtyp stimmt nicht überein.");
        }
        String title = normalizeRequired(response.title());
        String description = normalizeOptional(response.description());
        if (title == null || title.length() > MAX_TITLE_LENGTH) {
            issues.add("IMPROVEMENT_TITLE | $.title | Der Titel ist leer oder zu lang.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            issues.add("IMPROVEMENT_DESCRIPTION | $.description | Die Beschreibung ist zu lang.");
        }
        if (expectedType != AiImprovementElementType.TASK
                && (response.priority() != null || response.estimatedHours() != null || response.startDate() != null)) {
            issues.add("IMPROVEMENT_FIELDS | $ | Nicht freigegebene Felder sind für diesen Elementtyp belegt.");
        }
        if (expectedType == AiImprovementElementType.SECTION && response.dueDate() != null) {
            issues.add("IMPROVEMENT_FIELDS | $.dueDate | Projektbereiche besitzen kein Fälligkeitsdatum.");
        }
        if (expectedType == AiImprovementElementType.TASK) {
            if (response.priority() == null) {
                issues.add("IMPROVEMENT_PRIORITY | $.priority | Die Aufgabenpriorität fehlt.");
            }
            if (response.estimatedHours() != null
                    && (response.estimatedHours() < 1 || response.estimatedHours() > MAX_ESTIMATED_HOURS)) {
                issues.add("IMPROVEMENT_EFFORT | $.estimatedHours | Der Aufwand ist ungültig.");
            }
            if (response.startDate() != null && response.dueDate() != null
                    && response.dueDate().isBefore(response.startDate())) {
                issues.add("IMPROVEMENT_DATES | $.dueDate | Das Fälligkeitsdatum liegt vor dem Startdatum.");
            }
        }
        if (!issues.isEmpty()) throw invalid(issues);
        return new AiImprovementContent(expectedType, title, description, response.priority(),
                response.estimatedHours(), response.startDate(), response.dueDate());
    }

    private void unchanged(List<String> issues, String field, Object original, Object proposed) {
        if (!Objects.equals(original, proposed)) {
            issues.add("IMPROVEMENT_UNREQUESTED_FIELD | $." + field
                    + " | Das Feld ist für diese Aktion nicht freigegeben.");
        }
    }

    private boolean approximatelySameTextLength(AiImprovementContent original, AiImprovementContent proposed) {
        int originalLength = textLength(original.title(), original.description());
        int proposedLength = textLength(proposed.title(), proposed.description());
        int allowedDifference = Math.max(60, originalLength / 2);
        return Math.abs(originalLength - proposedLength) <= allowedDifference;
    }

    private int textLength(String title, String description) {
        return (title == null ? 0 : title.trim().length())
                + (description == null ? 0 : description.trim().length());
    }

    private String normalizeRequired(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private AiOutputValidationException invalid(List<String> issues) {
        return new AiOutputValidationException("Der KI-Vorschlag enthält ungültige Planelementdaten.", issues);
    }
}

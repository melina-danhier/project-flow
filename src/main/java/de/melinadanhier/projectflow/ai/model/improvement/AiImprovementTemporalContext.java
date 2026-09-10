package de.melinadanhier.projectflow.ai.model.improvement;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministische Erkennung, ob REPLAN Datumswerte aus vorhandenem Kontext ableiten darf. */
public final class AiImprovementTemporalContext {

    private static final Pattern TEMPORAL_HINT = Pattern.compile(
            "(?:\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b|\\b\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\b|"
                    + "\\b\\d{1,2}\\.\\s*(?:januar|februar|märz|april|mai|juni|juli|august|september|"
                    + "oktober|november|dezember)\\s+\\d{4}\\b)");

    private AiImprovementTemporalContext() {
    }

    public static boolean isAvailable(AiImprovementRequest request) {
        if (request == null) return false;
        AiImprovementProjectContext project = request.project();
        if (project != null && (project.startDate() != null || project.endDate() != null
                || containsHint(project.title()) || containsHint(project.description()))) {
            return true;
        }
        if (containsHint(request.comment())) return true;
        AiImprovementPlanContext plan = request.plan();
        if (plan == null) return false;
        return plan.sections().stream().anyMatch(section ->
                containsHint(section.title()) || containsHint(section.description())
                        || section.elements().stream().anyMatch(element ->
                                element.startDate() != null || element.dueDate() != null
                                        || containsHint(element.title()) || containsHint(element.description())));
    }

    private static boolean containsHint(String value) {
        return value != null && TEMPORAL_HINT.matcher(value.toLowerCase(Locale.GERMAN)).find();
    }
}

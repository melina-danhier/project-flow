package de.melinadanhier.projectflow.planelement.dto.improvement;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Erzeugt den aktionsspezifischen, feldweisen Diff für die Review-Ansicht. */
public record AiImprovementReview(List<AiImprovementFieldChange> changes) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public AiImprovementReview {
        changes = List.copyOf(changes);
    }

    public static AiImprovementReview from(AiImprovementProposal proposal) {
        List<AiImprovementFieldChange> changes = new ArrayList<>();
        AiImprovementContent original = proposal.original();
        AiImprovementContent proposed = proposal.proposed();
        switch (proposal.feedbackType()) {
            case IMPROVE, EXPAND, SIMPLIFY -> {
                addIfChanged(changes, "title", "Titel", original.title(), proposed.title(), "Nicht festgelegt");
                addIfChanged(changes, "description", "Beschreibung", original.description(),
                        proposed.description(), "Keine Beschreibung");
            }
            case REPLAN -> addReplanChanges(changes, proposal, original, proposed);
            case ESTIMATE_EFFORT -> addIfChanged(changes, "estimated-hours", "Aufwand",
                    original.estimatedHours(), proposed.estimatedHours(), AiImprovementReview::hours);
        }
        return new AiImprovementReview(changes);
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    private static void addReplanChanges(
            List<AiImprovementFieldChange> changes,
            AiImprovementProposal proposal,
            AiImprovementContent original,
            AiImprovementContent proposed
    ) {
        AiReplanPlacementProposal placement = proposal.placement();
        if (placement != null) {
            if (placement.changePlacement()) {
                addIfChanged(changes, "ordering", "Reihenfolge", placement.originalPositionLabel(),
                        placement.proposedPositionLabel(), "Nicht festgelegt");
            }
            addIfChanged(changes, "section", "Section", placement.originalSectionId(), placement.targetSectionId(),
                    ignored -> placement.originalSectionLabel(), ignored -> placement.targetSectionLabel());
        }
        if (proposal.elementType() == AiImprovementElementType.TASK) {
            addIfChanged(changes, "start-date", "Startdatum", original.startDate(), proposed.startDate(),
                    AiImprovementReview::date);
        }
        addIfChanged(changes, "due-date", "Fälligkeit", original.dueDate(), proposed.dueDate(),
                AiImprovementReview::date);
    }

    private static void addIfChanged(
            List<AiImprovementFieldChange> changes,
            String key,
            String label,
            String original,
            String proposed,
            String emptyValue
    ) {
        addIfChanged(changes, key, label, original, proposed,
                value -> value == null ? emptyValue : value);
    }

    private static <T> void addIfChanged(
            List<AiImprovementFieldChange> changes,
            String key,
            String label,
            T original,
            T proposed,
            Function<T, String> formatter
    ) {
        addIfChanged(changes, key, label, original, proposed, formatter, formatter);
    }

    private static <T> void addIfChanged(
            List<AiImprovementFieldChange> changes,
            String key,
            String label,
            T original,
            T proposed,
            Function<T, String> originalFormatter,
            Function<T, String> proposedFormatter
    ) {
        if (!Objects.equals(original, proposed)) {
            changes.add(new AiImprovementFieldChange(key, label,
                    originalFormatter.apply(original), proposedFormatter.apply(proposed)));
        }
    }

    private static String date(LocalDate value) {
        return value == null ? "Nicht festgelegt" : value.format(DATE_FORMAT);
    }

    private static String hours(Integer value) {
        return value == null ? "Nicht festgelegt" : value + " Stunden";
    }
}

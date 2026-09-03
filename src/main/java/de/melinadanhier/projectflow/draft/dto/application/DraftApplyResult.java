package de.melinadanhier.projectflow.draft.dto.application;

import java.util.Objects;

public record DraftApplyResult(
        DraftApplyStatus status,
        DraftApplicationSummary summary
) {
    public DraftApplyResult {
        Objects.requireNonNull(status, "status");
        if (status == DraftApplyStatus.APPLIED && summary != null) {
            throw new IllegalArgumentException("APPLIED darf keine Bestätigungsdaten enthalten.");
        }
        if (status != DraftApplyStatus.APPLIED && summary == null) {
            throw new IllegalArgumentException("Eine erforderliche Bestätigung benötigt eine Zusammenfassung.");
        }
    }

    public static DraftApplyResult applied() {
        return new DraftApplyResult(DraftApplyStatus.APPLIED, null);
    }

    public static DraftApplyResult confirmationRequired(
            DraftApplyStatus status,
            DraftApplicationSummary summary
    ) {
        if (status == DraftApplyStatus.APPLIED) {
            throw new IllegalArgumentException("APPLIED erfordert keine Bestätigung.");
        }
        return new DraftApplyResult(status, summary);
    }
}

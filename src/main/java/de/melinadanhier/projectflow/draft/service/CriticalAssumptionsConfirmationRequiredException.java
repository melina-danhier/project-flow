package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import lombok.Getter;

@Getter
public class CriticalAssumptionsConfirmationRequiredException extends RuntimeException {
    private final DraftReviewDto draft;
    private final boolean includePending;

    public CriticalAssumptionsConfirmationRequiredException(DraftReviewDto draft, boolean includePending) {
        super("Bitte bestätige die noch ungeprüften kritischen Annahmen.");
        this.draft = draft;
        this.includePending = includePending;
    }
}

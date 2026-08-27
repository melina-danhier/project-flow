package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import lombok.Getter;

@Getter
public class CriticalAssumptionsConfirmationRequiredException extends RuntimeException {
    private final DraftReviewDto draft;

    public CriticalAssumptionsConfirmationRequiredException(DraftReviewDto draft) {
        super("Bitte bestätige die noch ungeprüften kritischen Annahmen.");
        this.draft = draft;
    }
}

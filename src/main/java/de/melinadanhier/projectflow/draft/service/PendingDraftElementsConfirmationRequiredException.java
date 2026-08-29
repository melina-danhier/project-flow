package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import lombok.Getter;

@Getter
public class PendingDraftElementsConfirmationRequiredException extends RuntimeException {
    private final DraftReviewDto draft;

    public PendingDraftElementsConfirmationRequiredException(DraftReviewDto draft) {
        super(draft.getPendingElementCount() + " Elemente wurden noch nicht überprüft.");
        this.draft = draft;
    }
}

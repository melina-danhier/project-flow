package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;

public class DraftVersionConflictException extends ConflictException {
    private final boolean reviewAvailable;

    public DraftVersionConflictException(String message) {
        this(message, true);
    }

    public DraftVersionConflictException(String message, boolean reviewAvailable) {
        super(message);
        this.reviewAvailable = reviewAvailable;
    }

    public boolean isReviewAvailable() {
        return reviewAvailable;
    }
}

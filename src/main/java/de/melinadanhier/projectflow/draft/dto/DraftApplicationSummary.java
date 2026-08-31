package de.melinadanhier.projectflow.draft.dto;

import java.util.UUID;

public record DraftApplicationSummary(
        UUID draftId,
        UUID projectId,
        String projectTitle,
        long lockVersion,
        int pendingElementCount,
        int omittedDependencyCount,
        int includedSectionCount,
        int includedElementCount
) {
    public boolean empty() {
        return includedSectionCount == 0 && includedElementCount == 0;
    }
}

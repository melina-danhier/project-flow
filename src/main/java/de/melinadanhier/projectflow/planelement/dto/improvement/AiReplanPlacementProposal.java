package de.melinadanhier.projectflow.planelement.dto.improvement;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;

import java.io.Serializable;
import java.util.UUID;

/** Validierter, temporärer Platzierungsvorschlag samt nutzerlesbaren Review-Angaben. */
public record AiReplanPlacementProposal(
        boolean changePlacement,
        UUID originalSectionId,
        String originalSectionLabel,
        String originalPositionLabel,
        UUID targetSectionId,
        String targetSectionLabel,
        String proposedPositionLabel,
        UUID beforeElementId,
        UUID afterElementId,
        SortMode sortMode,
        boolean dateOrderingActive
) implements Serializable {

    public AiReplanPlacementProposal(
            boolean changePlacement,
            UUID originalSectionId,
            String originalSectionLabel,
            String originalPositionLabel,
            UUID targetSectionId,
            String targetSectionLabel,
            String proposedPositionLabel,
            UUID beforeElementId,
            UUID afterElementId,
            SortMode sortMode
    ) {
        this(changePlacement, originalSectionId, originalSectionLabel, originalPositionLabel,
                targetSectionId, targetSectionLabel, proposedPositionLabel, beforeElementId, afterElementId,
                sortMode, sortMode == SortMode.DATE);
    }
}

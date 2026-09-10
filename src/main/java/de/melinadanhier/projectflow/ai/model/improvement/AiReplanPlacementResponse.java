package de.melinadanhier.projectflow.ai.model.improvement;

/** Fachliche Platzierung ohne technische sortOrder-Werte. */
public record AiReplanPlacementResponse(
        boolean changePlacement,
        String targetSectionId,
        String beforeElementId,
        String afterElementId
) {
    public static AiReplanPlacementResponse unchanged() {
        return new AiReplanPlacementResponse(false, null, null, null);
    }
}

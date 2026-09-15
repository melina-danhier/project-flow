package de.melinadanhier.projectflow.study.domain;

public enum StudyEventType {
    PLAN_GENERATED,
    DRAFT_ITEM_ACCEPTED,
    DRAFT_ITEM_REJECTED,
    DRAFT_ITEM_EDITED,
    PLAN_REGENERATED,
    PLAN_ADOPTED,
    LOCAL_AI_CHANGE_STARTED,
    LOCAL_AI_CHANGE_ADOPTED,
    LOCAL_AI_CHANGE_REJECTED,
    PLAN_AI_CHANGE_STARTED,
    PLAN_AI_CHANGE_ADOPTED,
    PLAN_AI_CHANGE_REJECTED,
    // Kept for compatibility with study events recorded before change scopes were separated.
    @Deprecated
    AI_EDIT_STARTED,
    @Deprecated
    AI_EDIT_ADOPTED,
    @Deprecated
    AI_EDIT_REJECTED
}

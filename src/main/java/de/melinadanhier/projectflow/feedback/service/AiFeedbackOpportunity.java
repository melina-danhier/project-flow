package de.melinadanhier.projectflow.feedback.service;

import de.melinadanhier.projectflow.feedback.domain.AiFeedbackContext;
import java.io.Serializable;
import java.util.UUID;

public record AiFeedbackOpportunity(AiFeedbackContext context, UUID actionId, String returnUrl)
        implements Serializable {
    public static final String SESSION_ATTRIBUTE = "aiFeedbackOpportunity";
}

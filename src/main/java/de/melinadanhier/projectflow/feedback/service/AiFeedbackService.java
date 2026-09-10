package de.melinadanhier.projectflow.feedback.service;

import de.melinadanhier.projectflow.feedback.domain.AiFeedback;
import de.melinadanhier.projectflow.feedback.dto.AiFeedbackForm;
import de.melinadanhier.projectflow.feedback.repository.AiFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiFeedbackService {
    private final AiFeedbackRepository repository;
    private final Clock clock;

    @Transactional
    public void saveIfAbsent(UUID userId, AiFeedbackOpportunity opportunity, AiFeedbackForm form) {
        if (repository.existsByUserIdAndContextAndActionId(
                userId, opportunity.context(), opportunity.actionId())) return;
        AiFeedback feedback = new AiFeedback();
        feedback.setUserId(userId);
        feedback.setContext(opportunity.context());
        feedback.setActionId(opportunity.actionId());
        feedback.setRating(form.getRating());
        feedback.setComment(form.getComment() == null || form.getComment().isBlank()
                ? null : form.getComment().trim());
        feedback.setCreatedAt(Instant.now(clock));
        repository.save(feedback);
    }
}

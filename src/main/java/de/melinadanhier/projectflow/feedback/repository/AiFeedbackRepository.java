package de.melinadanhier.projectflow.feedback.repository;

import de.melinadanhier.projectflow.feedback.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {
    boolean existsByUserIdAndContextAndActionId(UUID userId, AiFeedbackContext context, UUID actionId);
}

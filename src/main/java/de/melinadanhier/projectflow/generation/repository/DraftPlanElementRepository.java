package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.DraftPlanElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface DraftPlanElementRepository extends JpaRepository<DraftPlanElement, UUID> {
    List<DraftPlanElement> findAllByPlanDraftIdOrderBySortOrderAsc(UUID planDraftId);
}

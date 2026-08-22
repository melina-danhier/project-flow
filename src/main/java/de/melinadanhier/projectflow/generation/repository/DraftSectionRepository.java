package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.DraftSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface DraftSectionRepository extends JpaRepository<DraftSection, UUID> {
    List<DraftSection> findAllByPlanDraftIdOrderBySortOrderAsc(UUID planDraftId);
}

package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.DraftPlanElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftPlanElementRepository extends JpaRepository<DraftPlanElement, UUID> {
}

package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.DraftSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftSectionRepository extends JpaRepository<DraftSection, UUID> {
}

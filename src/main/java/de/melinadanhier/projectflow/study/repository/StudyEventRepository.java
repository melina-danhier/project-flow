package de.melinadanhier.projectflow.study.repository;

import de.melinadanhier.projectflow.study.domain.StudyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StudyEventRepository extends JpaRepository<StudyEvent, UUID> {
}

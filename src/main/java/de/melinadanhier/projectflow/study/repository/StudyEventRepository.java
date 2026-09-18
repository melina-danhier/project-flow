package de.melinadanhier.projectflow.study.repository;

import de.melinadanhier.projectflow.study.domain.StudyEvent;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.domain.StudyPhase;
import de.melinadanhier.projectflow.study.domain.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.UUID;

public interface StudyEventRepository extends JpaRepository<StudyEvent, UUID> {
    boolean existsByStudySessionAndStudyPhaseAndEventTypeIn(
            StudySession studySession,
            StudyPhase studyPhase,
            Collection<StudyEventType> eventTypes
    );
}

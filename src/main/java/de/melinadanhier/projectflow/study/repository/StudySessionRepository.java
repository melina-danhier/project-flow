package de.melinadanhier.projectflow.study.repository;

import de.melinadanhier.projectflow.study.domain.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    Optional<StudySession> findFirstByParticipantIdAndCompletedAtIsNullOrderByStartedAtDesc(
            String participantId
    );
}
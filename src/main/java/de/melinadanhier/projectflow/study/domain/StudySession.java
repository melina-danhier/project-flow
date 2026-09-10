package de.melinadanhier.projectflow.study.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "study_sessions")
@Getter @Setter @NoArgsConstructor
public class StudySession {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "participant_id", nullable = false, length = 64)
    private String participantId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "project_id")
    private UUID projectId;
}

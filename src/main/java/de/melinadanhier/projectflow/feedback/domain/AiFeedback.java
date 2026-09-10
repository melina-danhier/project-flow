package de.melinadanhier.projectflow.feedback.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_feedback_action", columnNames = {"user_id", "context", "action_id"}))
@Getter @Setter @NoArgsConstructor
public class AiFeedback {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeedbackContext context;
    @Column(name = "action_id", nullable = false)
    private UUID actionId;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int rating;
    @Column(length = 2000)
    private String comment;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiWorkflowCompletionTokenRepository
        extends JpaRepository<AiWorkflowCompletionToken, UUID> {

    @Query("""
            select token.workflow.id
            from AiWorkflowCompletionToken token
            join token.workflow.project.memberships membership
            where token.completionToken = :completionToken
              and membership.user.id = :userId
              and membership.active = true
            """)
    Optional<UUID> findOwnedWorkflowIdByToken(
            @Param("completionToken") UUID completionToken,
            @Param("userId") UUID userId
    );
}

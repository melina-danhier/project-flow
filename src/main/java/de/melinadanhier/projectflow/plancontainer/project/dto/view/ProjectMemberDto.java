package de.melinadanhier.projectflow.plancontainer.project.dto.view;

import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMemberRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProjectMemberDto {

    private UUID id;
    private UUID userId;
    private String displayName;
    private ProjectMemberRole role;
    private Instant joinedAt;
    private boolean active;
}

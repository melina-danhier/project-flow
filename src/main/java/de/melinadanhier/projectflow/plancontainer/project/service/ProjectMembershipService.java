package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectMemberDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.AddProjectMemberForm;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapper;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectMembershipService {

    static final int MAX_ACTIVE_MEMBERS = 10;

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectMapper projectMapper;

    @Transactional
    public ProjectMemberDto addMember(UUID projectId, AddProjectMemberForm form, UUID actingUserId) {
        return projectMapper.toMemberDto(addMember(projectId, form.getEmail(), actingUserId));
    }

    @Transactional
    public ProjectMember addMember(UUID projectId, String email, UUID actingUserId) {
        authorizationService.requireEditableOwner(projectId, actingUserId);
        Project project = lockActiveOwner(projectId).getProject();
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Unter dieser E-Mail-Adresse wurde kein Konto gefunden."));

        ProjectMember existingMembership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, user.getId())
                .orElse(null);
        if (existingMembership != null && existingMembership.isActive()) {
            throw new ConflictException("Diese Person ist bereits aktives Projektmitglied.");
        }
        if (projectMemberRepository.countByProjectIdAndActiveTrue(projectId) >= MAX_ACTIVE_MEMBERS) {
            throw new ConflictException("Ein Projekt kann höchstens zehn aktive Mitglieder haben.");
        }
        if (existingMembership != null) {
            existingMembership.setActive(true);
            return existingMembership;
        }

        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setRole(ProjectMemberRole.MEMBER);
        membership.setActive(true);
        project.addMembership(membership);
        return projectMemberRepository.save(membership);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID memberUserId, UUID actingOwnerId) {
        authorizationService.requireEditableOwner(projectId, actingOwnerId);
        lockActiveOwner(projectId);
        ProjectMember membership = projectMemberRepository
                .findByIdAndProjectIdAndActiveTrue(memberUserId, projectId)
                .orElseGet(() -> requireActiveMembership(projectId, memberUserId));
        deactivateMember(membership);
    }

    @Transactional
    public void leaveProject(UUID projectId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
        lockActiveOwner(projectId);
        ProjectMember membership = requireActiveMembership(projectId, userId);
        deactivateMember(membership);
    }

    private void deactivateMember(ProjectMember membership) {
        if (membership.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenOperationException("Der Projekteigentümer kann nicht entfernt werden oder das Projekt verlassen.");
        }
        membership.setActive(false);
        taskRepository.clearAssignee(membership.getId());
    }

    private ProjectMember requireActiveMembership(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserIdAndActiveTrue(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Projektmitglied wurde nicht gefunden."));
    }

    private ProjectMember lockActiveOwner(UUID projectId) {
        return projectMemberRepository.findActiveOwnerForUpdate(projectId, ProjectMemberRole.OWNER)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

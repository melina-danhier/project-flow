package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DraftReviewService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftMapper draftMapper;
    private final ProjectAuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId) {
        DraftPlan draft = planDraftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ConflictException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."));
        authorizationService.requireOwner(projectId, userId);
        return draftMapper.toReviewDto(draft);
    }
}

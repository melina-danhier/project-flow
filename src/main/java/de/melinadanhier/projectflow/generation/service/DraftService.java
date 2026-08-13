package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.mapper.DraftMapper;
import de.melinadanhier.projectflow.generation.repository.DraftPlanElementRepository;
import de.melinadanhier.projectflow.generation.repository.DraftSectionRepository;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DraftService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftSectionRepository draftSectionRepository;
    private final DraftPlanElementRepository draftPlanElementRepository;
    private final DraftMapper draftMapper;
}

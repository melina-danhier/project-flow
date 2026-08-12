package com.melina.projectflow.generation.service;

import com.melina.projectflow.generation.mapper.DraftMapper;
import com.melina.projectflow.generation.repository.DraftPlanElementRepository;
import com.melina.projectflow.generation.repository.DraftSectionRepository;
import com.melina.projectflow.generation.repository.PlanDraftRepository;
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

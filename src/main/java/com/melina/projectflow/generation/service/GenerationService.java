package com.melina.projectflow.generation.service;

import com.melina.projectflow.generation.mapper.DraftMapper;
import com.melina.projectflow.generation.parser.GenerationResponseParser;
import com.melina.projectflow.generation.prompt.GenerationPromptBuilder;
import com.melina.projectflow.generation.repository.PlanDraftRepository;
import com.melina.projectflow.generation.validation.GeneratedPlanValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerationService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftMapper draftMapper;
    private final GenerationPromptBuilder promptBuilder;
    private final GenerationResponseParser responseParser;
    private final GeneratedPlanValidator generatedPlanValidator;
}

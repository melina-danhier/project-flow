package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.mapper.DraftMapper;
import de.melinadanhier.projectflow.generation.parser.GenerationResponseParser;
import de.melinadanhier.projectflow.generation.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.validation.GeneratedPlanValidator;
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

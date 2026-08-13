package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.service.DraftService;
import de.melinadanhier.projectflow.generation.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;
    private final DraftService draftService;
}

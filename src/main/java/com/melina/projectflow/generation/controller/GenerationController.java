package com.melina.projectflow.generation.controller;

import com.melina.projectflow.generation.service.DraftService;
import com.melina.projectflow.generation.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;
    private final DraftService draftService;
}

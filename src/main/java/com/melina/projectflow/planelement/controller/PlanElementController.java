package com.melina.projectflow.planelement.controller;

import com.melina.projectflow.planelement.service.PlanElementService;
import com.melina.projectflow.planelement.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PlanElementController {

    private final PlanElementService planElementService;
    private final SectionService sectionService;
}

package com.melina.projectflow.plancontainer.template.controller;

import com.melina.projectflow.plancontainer.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
}

package de.melinadanhier.projectflow.plancontainer.template.controller;

import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/templates")
    public String overview(Model model) {
        model.addAttribute("templates", templateService.getTemplates());
        return "templates/overview";
    }

    @GetMapping("/templates/{templateId}")
    public String detail(@PathVariable UUID templateId, Model model) {
        model.addAttribute("template", templateService.getTemplate(templateId));
        return "templates/detail";
    }
}

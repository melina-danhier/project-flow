package de.melinadanhier.projectflow.plancontainer.template.controller;

import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/templates")
    public String overview(
            @RequestParam(required = false) ProjectCategory category,
            Model model
    ) {
        populateCatalog(model, category,
                category == null ? templateService.getTemplates() : templateService.getTemplates(category),
                false, null);
        return "templates/overview";
    }

    @GetMapping("/templates/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        populateCatalog(model, null, templateService.search(q), true, q);
        return "templates/overview";
    }

    @GetMapping("/templates/{templateId}")
    public String detail(
            @PathVariable UUID templateId,
            @RequestParam(required = false) ProjectCategory category,
            @RequestParam(required = false) String q,
            Model model
    ) {
        model.addAttribute("template", templateService.getTemplate(templateId));
        model.addAttribute("wizardContext", false);
        model.addAttribute("backUrl", q != null
                ? UriComponentsBuilder.fromPath("/templates/search").queryParam("q", q).build().encode().toUriString()
                : UriComponentsBuilder.fromPath("/templates").queryParamIfPresent("category", java.util.Optional.ofNullable(category))
                        .build().encode().toUriString());
        return "templates/detail";
    }

    private void populateCatalog(Model model, ProjectCategory selectedCategory,
                                 java.util.List<?> templates, boolean search, String query) {
        model.addAttribute("categories", ProjectCategory.values());
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("templates", templates);
        model.addAttribute("searchPage", search);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("wizardContext", false);
    }
}

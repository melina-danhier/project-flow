package de.melinadanhier.projectflow.plancontainer.project.controller;

import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectMembershipService;
import de.melinadanhier.projectflow.plancontainer.project.dto.AddProjectMemberForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectUpdateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMembershipService membershipService;

    @GetMapping("/projects")
    public String projects(
            @RequestParam(required = false) ProjectLocation location,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        ProjectLocation selectedLocation = location == null ? ProjectLocation.OVERVIEW : location;
        model.addAttribute("projects", projectService.findAccessibleProjects(selectedLocation, currentUser.userId()));
        model.addAttribute("selectedLocation", selectedLocation);
        return "projects/overview";
    }

    @GetMapping("/projects/new")
    public String createProjectForm(Model model) {
        ProjectCreateForm form = new ProjectCreateForm();
        form.setCreationType(CreationType.EMPTY);
        model.addAttribute("projectForm", form);
        return "projects/create";
    }

    @PostMapping("/projects")
    public String createProject(
            @Valid @ModelAttribute("projectForm") ProjectCreateForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "projects/create";
        }
        ProjectDetailsDto created = projectService.createProject(form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde angelegt.");
        return "redirect:/projects/" + created.getId() + "/plan";
    }

    @GetMapping("/projects/{projectId}/edit")
    public String editForm(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        ProjectDetailsDto project = projectService.getProject(projectId, currentUser.userId());
        ProjectUpdateForm form = new ProjectUpdateForm();
        form.setTitle(project.getTitle());
        form.setDescription(project.getDescription());
        form.setStartDate(project.getStartDate());
        form.setEndDate(project.getEndDate());
        form.setStructureMode(project.getStructureMode());
        form.setSortMode(project.getSortMode());
        form.setLockVersion(project.getLockVersion());
        model.addAttribute("project", project);
        model.addAttribute("projectForm", form);
        return "projects/edit";
    }

    @PostMapping("/projects/{projectId}/edit")
    public String updateProject(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("projectForm") ProjectUpdateForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("project", projectService.getProject(projectId, currentUser.userId()));
            return "projects/edit";
        }
        projectService.updateProject(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde aktualisiert.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/trash")
    public String moveToTrash(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        projectService.moveToTrash(projectId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde in den Papierkorb verschoben.");
        return "redirect:/projects";
    }

    @PostMapping("/projects/{projectId}/reactivate")
    public String reactivate(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        projectService.reactivateProject(projectId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde wiederhergestellt.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/delete")
    public String deletePermanently(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        projectService.deleteProjectPermanently(projectId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde endgültig gelöscht.");
        return "redirect:/projects?location=TRASH";
    }

    @PostMapping("/projects/{projectId}/members")
    public String addMember(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("memberForm") AddProjectMemberForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bitte gib eine gültige E-Mail-Adresse an.");
            return "redirect:/projects/" + projectId;
        }
        membershipService.addMember(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektmitglied wurde hinzugefügt.");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/projects/{projectId}/members/{memberId}/remove")
    public String removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        membershipService.removeMember(projectId, memberId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektmitglied wurde entfernt.");
        return "redirect:/projects/" + projectId;
    }
}

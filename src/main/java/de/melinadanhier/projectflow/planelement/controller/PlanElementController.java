package de.melinadanhier.projectflow.planelement.controller;

import de.melinadanhier.projectflow.planelement.service.PlanElementService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.planelement.service.TaskDependencyService;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PlanElementController {

    private final TaskService taskService;
    private final SectionService sectionService;
    private final TaskDependencyService dependencyService;

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    public String taskDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        TaskDetailsDto task = taskService.getTaskDetail(projectId, taskId, currentUser.userId());
        model.addAttribute("task", task);
        model.addAttribute("taskForm", toForm(task));
        return "planelements/task-detail";
    }

    @PostMapping("/projects/{projectId}/tasks")
    public String createTask(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("taskForm") TaskForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Die Aufgabe enthält ungültige Angaben.");
            return projectRedirect(projectId);
        }
        taskService.createTask(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde angelegt.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/edit")
    public String updateTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @ModelAttribute("taskForm") TaskForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Die Aufgabe enthält ungültige Angaben.");
            return "redirect:/projects/" + projectId + "/tasks/" + taskId;
        }
        taskService.updateTask(projectId, taskId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde aktualisiert.");
        return "redirect:/projects/" + projectId + "/tasks/" + taskId;
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/delete")
    public String deleteTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        taskService.deleteTask(projectId, taskId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde endgültig gelöscht.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/sections")
    public String createSection(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("sectionForm") SectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Die Projektphase enthält ungültige Angaben.");
            return projectRedirect(projectId);
        }
        sectionService.createSection(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektphase wurde angelegt.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/sections/{sectionId}/edit")
    public String updateSection(
            @PathVariable UUID projectId,
            @PathVariable UUID sectionId,
            @Valid @ModelAttribute("sectionForm") SectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Die Projektphase enthält ungültige Angaben.");
            return projectRedirect(projectId);
        }
        sectionService.updateSection(projectId, sectionId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektphase wurde aktualisiert.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/sections/{sectionId}/delete")
    public String deleteSection(
            @PathVariable UUID projectId,
            @PathVariable UUID sectionId,
            @Valid @ModelAttribute("deleteSectionForm") DeleteSectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bitte wähle aus, was mit den Inhalten geschehen soll.");
            return projectRedirect(projectId);
        }
        sectionService.deleteSection(projectId, sectionId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektphase wurde gelöscht.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/dependencies")
    public String createDependency(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("dependencyForm") TaskDependencyForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bitte wähle zwei gültige Aufgaben.");
            return projectRedirect(projectId);
        }
        dependencyService.createDependency(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabenabhängigkeit wurde angelegt.");
        return projectRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/dependencies/{successorTaskId}/{prerequisiteTaskId}/delete")
    public String deleteDependency(
            @PathVariable UUID projectId,
            @PathVariable UUID successorTaskId,
            @PathVariable UUID prerequisiteTaskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        dependencyService.deleteDependency(
                projectId, successorTaskId, prerequisiteTaskId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabenabhängigkeit wurde gelöscht.");
        return projectRedirect(projectId);
    }

    private String projectRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId;
    }

    private TaskForm toForm(TaskDetailsDto task) {
        TaskForm form = new TaskForm();
        form.setPlanSectionId(task.getPlanSectionId());
        form.setTitle(task.getTitle());
        form.setDescription(task.getDescription());
        form.setSortOrder(task.getSortOrder());
        form.setPriority(task.getPriority());
        form.setStatus(task.getStatus());
        form.setStartDate(task.getStartDate());
        form.setDueDate(task.getDueDate());
        form.setAssigneeId(task.getAssigneeId());
        form.setLockVersion(task.getLockVersion());
        return form;
    }
}

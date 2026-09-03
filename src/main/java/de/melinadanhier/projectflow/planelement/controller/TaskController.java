package de.melinadanhier.projectflow.planelement.controller;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyForm;
import de.melinadanhier.projectflow.planelement.dto.TaskCommentForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.service.TaskDependencyService;
import de.melinadanhier.projectflow.planelement.service.TaskCommentService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
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
public class TaskController {

    private final TaskService taskService;
    private final TaskDependencyService dependencyService;
    private final TaskCommentService commentService;

    @GetMapping("/projects/{projectId}/tasks/new")
    public String createForm(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        TaskForm form = new TaskForm();
        form.setPriority(TaskPriority.MEDIUM);
        form.setStatus(TaskStatus.OPEN);
        model.addAttribute("taskForm", form);
        populateFormModel(model, taskService.getTaskCreationContext(projectId, currentUser.userId()), false);
        return "projects/tasks/form";
    }

    @PostMapping("/projects/{projectId}/tasks")
    public String create(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("taskForm") TaskForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, taskService.getTaskCreationContext(projectId, currentUser.userId()), false);
            return "projects/tasks/form";
        }
        try {
            TaskDetailsDto created = taskService.createTask(projectId, form, currentUser.userId());
            redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde angelegt.");
            return taskRedirect(projectId, created.getId());
        } catch (DomainValidationException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            populateFormModel(model, taskService.getTaskCreationContext(projectId, currentUser.userId()), false);
            return "projects/tasks/form";
        }
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    public String detail(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        populateDetailModel(model, projectId, taskId, currentUser.userId());
        model.addAttribute("dependencyForm", new TaskDependencyForm());
        return "projects/tasks/detail";
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments")
    public String addComment(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @ModelAttribute("commentForm") TaskCommentForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateDetailModel(model, projectId, taskId, currentUser.userId());
            return "projects/tasks/detail";
        }
        commentService.addComment(projectId, taskId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Beitrag wurde hinzugefügt.");
        return taskRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments/{commentId}/delete")
    public String deleteComment(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        commentService.deleteOwnComment(projectId, taskId, commentId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Beitrag wurde gelöscht.");
        return taskRedirect(projectId, taskId);
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}/edit")
    public String editForm(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        TaskDetailsDto task = taskService.getTaskForEditing(projectId, taskId, currentUser.userId());
        model.addAttribute("taskForm", toForm(task));
        populateFormModel(model, task, true);
        return "projects/tasks/form";
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}")
    public String update(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Validated({jakarta.validation.groups.Default.class, UpdateValidation.class})
            @ModelAttribute("taskForm") TaskForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, taskService.getTaskForEditing(projectId, taskId, currentUser.userId()), true);
            return "projects/tasks/form";
        }
        try {
            taskService.updateTask(projectId, taskId, form, currentUser.userId());
            redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde aktualisiert.");
            return taskRedirect(projectId, taskId);
        } catch (DomainValidationException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            populateFormModel(model, taskService.getTaskForEditing(projectId, taskId, currentUser.userId()), true);
            return "projects/tasks/form";
        }
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/delete")
    public String delete(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        taskService.deleteTask(projectId, taskId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Aufgabe wurde endgültig gelöscht.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/dependencies")
    public String addDependency(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @ModelAttribute("dependencyForm") TaskDependencyForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        form.setSuccessorTaskId(taskId);
        if (bindingResult.hasErrors()) {
            populateDetailModel(model, projectId, taskId, currentUser.userId());
            return "projects/tasks/detail";
        }
        try {
            dependencyService.createDependency(projectId, form, currentUser.userId());
        } catch (ConflictException | DomainValidationException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            populateDetailModel(model, projectId, taskId, currentUser.userId());
            return "projects/tasks/detail";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Voraussetzung wurde hinzugefügt.");
        return taskRedirect(projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/dependencies/{prerequisiteId}/remove")
    public String removeDependency(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID prerequisiteId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        dependencyService.deleteDependency(projectId, taskId, prerequisiteId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Voraussetzung wurde entfernt.");
        return taskRedirect(projectId, taskId);
    }

    private void populateDetailModel(Model model, UUID projectId, UUID taskId, UUID userId) {
        model.addAttribute("task", taskService.getTaskDetail(projectId, taskId, userId));
        var commentSection = commentService.getCommentSection(projectId, taskId, userId);
        model.addAttribute("comments", commentSection.comments());
        model.addAttribute("commentGroupProject", commentSection.groupProject());
        if (!model.containsAttribute("commentForm")) {
            model.addAttribute("commentForm", new TaskCommentForm());
        }
    }

    private void populateFormModel(Model model, TaskDetailsDto context, boolean editing) {
        model.addAttribute("projectId", context.getPlanContainerId());
        model.addAttribute("taskId", context.getId());
        model.addAttribute("sections", context.getAvailableSections());
        model.addAttribute("assignees", context.getAvailableAssignees());
        model.addAttribute("groupProject", context.isGroupProject());
        model.addAttribute("editing", editing);
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

    private String taskRedirect(UUID projectId, UUID taskId) {
        return "redirect:/projects/" + projectId + "/tasks/" + taskId;
    }

}

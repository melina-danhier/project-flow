package de.melinadanhier.projectflow.plancontainer.template;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapperImpl;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService.TemplateDateHandling;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateDetailsDto;
import de.melinadanhier.projectflow.plancontainer.template.mapper.TemplateMapperImpl;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.SectionDeletionMode;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapperImpl;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanReviewStatus;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        TemplateService.class,
        ProjectService.class,
        ProjectAuthorizationService.class,
        TaskService.class,
        SectionService.class,
        TemplateMapperImpl.class,
        ProjectMapperImpl.class,
        PlanElementMapperImpl.class
})
class StaticProjectTemplateIntegrationTest {

    private static final List<String> SECTION_TITLES = List.of(
            "Grundlagen festlegen",
            "Organisation vorbereiten",
            "Vorbereitung abschließen",
            "Veranstaltung durchführen",
            "Nachbereitung"
    );

    private static final List<List<String>> TASK_TITLES = List.of(
            List.of("Ziel und Art der Veranstaltung festlegen", "Teilnehmerkreis festlegen",
                    "Budgetrahmen bestimmen", "Termin festlegen", "Veranstaltungsort auswählen"),
            List.of("Gästeliste erstellen", "Einladungen versenden", "Essen und Getränke planen",
                    "Benötigte Materialien oder Ausstattung festlegen", "Einkäufe und Bestellungen planen",
                    "Aufgaben unter Beteiligten verteilen"),
            List.of("Rückmeldungen der Gäste prüfen", "Einkäufe erledigen", "Bestellungen kontrollieren",
                    "Veranstaltungsort vorbereiten", "Letzte offene Punkte prüfen"),
            List.of("Aufbau durchführen", "Veranstaltung durchführen", "Abbau und Aufräumen"),
            List.of("Offene Kosten prüfen", "Geliehene Gegenstände zurückgeben",
                    "Kurze Nachbereitung durchführen")
    );

    private static final List<String> MILESTONE_TITLES = List.of(
            "Rahmenbedingungen festgelegt",
            "Organisation vollständig geplant",
            "Veranstaltung vorbereitet",
            "Veranstaltung abgeschlossen"
    );

    @Autowired private TemplateRepository templateRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlanSectionRepository sectionRepository;
    @Autowired private PlanElementRepository elementRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private TemplateService templateService;
    @Autowired private ProjectService projectService;
    @Autowired private TaskService taskService;
    @Autowired private SectionService sectionService;
    @Autowired private EntityManager entityManager;

    @Test
    void loadsTheCompleteTemplateInSectionAndElementOrder() {
        Template template = saveStaticEventTemplate();
        entityManager.clear();

        TemplateDetailsDto details = templateService.getTemplate(template.getId());

        assertThat(details.getTitle()).isEqualTo("Kleine Veranstaltung planen");
        assertThat(details.getDescription()).isEqualTo(
                "Vorlage zur Planung einer kleineren privaten, studentischen oder ehrenamtlichen Veranstaltung.");
        assertThat(details.getSections())
                .extracting(SectionDto::getTitle)
                .containsExactlyElementsOf(SECTION_TITLES);
        assertThat(details.getSections())
                .extracting(SectionDto::getSortOrder)
                .containsExactly(100, 200, 300, 400, 500);
        assertThat(details.getTasks())
                .extracting(TaskDetailsDto::getTitle)
                .containsExactlyElementsOf(TASK_TITLES.stream().flatMap(List::stream).toList());
        assertThat(details.getMilestones())
                .extracting(MilestoneDetailsDto::getTitle)
                .containsExactlyElementsOf(MILESTONE_TITLES);
        assertThat(details.getTasks()).allSatisfy(task -> {
            assertThat(task.getStartDate()).isNull();
            assertThat(task.getDueDate()).isNull();
        });
        assertThat(details.getMilestones()).allSatisfy(milestone -> assertThat(milestone.getDueDate()).isNull());
    }

    @Test
    void createsAnEditableIndependentProjectCopy() {
        User owner = saveUser("template-owner@example.org");
        Template template = saveStaticEventTemplate();
        Set<UUID> templateSectionIds = template.getSections().stream().map(PlanSection::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> templateElementIds = template.getElements().stream().map(element -> element.getId()).collect(java.util.stream.Collectors.toSet());
        String originalTaskTitle = TASK_TITLES.getFirst().getFirst();
        UUID originalTaskId = template.getElements().stream()
                .filter(Task.class::isInstance)
                .map(Task.class::cast)
                .filter(task -> task.getTitle().equals(originalTaskTitle))
                .findFirst().orElseThrow().getId();

        var created = projectService.createProjectFromTemplate(template.getId(), projectForm(), owner.getId());
        entityManager.flush();
        entityManager.clear();

        Project project = projectRepository.findById(created.getId()).orElseThrow();
        List<PlanSection> copiedSections = sectionRepository
                .findAllByPlanContainerIdOrderBySortOrderAsc(project.getId());
        List<Task> copiedTasks = taskRepository.findPlanTasks(project.getId());
        List<Milestone> copiedMilestones = milestoneRepository
                .findAllByPlanContainerIdOrderBySortOrderAsc(project.getId());

        assertThat(project.getCreationType()).isEqualTo(CreationType.TEMPLATE);
        assertThat(project.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);
        assertThat(copiedSections).hasSize(5);
        assertThat(copiedTasks).hasSize(22);
        assertThat(copiedMilestones).hasSize(4);
        assertThat(copiedSections).extracting(PlanSection::getSortOrder)
                .containsExactly(100, 200, 300, 400, 500);
        assertThat(copiedSections).extracting(PlanSection::getId).doesNotContainAnyElementsOf(templateSectionIds);
        assertThat(copiedTasks).extracting(Task::getId).doesNotContainAnyElementsOf(templateElementIds);
        assertThat(copiedMilestones).extracting(Milestone::getId).doesNotContainAnyElementsOf(templateElementIds);
        assertThat(copiedTasks).allSatisfy(task -> assertThat(task.getPlanContainer().getId()).isEqualTo(project.getId()));
        assertThat(copiedMilestones).allSatisfy(milestone -> {
            assertThat(milestone.getPlanContainer().getId()).isEqualTo(project.getId());
            assertThat(milestone.getPlanSection()).isNotNull();
        });
        for (int index = 0; index < copiedSections.size(); index++) {
            int expectedElementCount = TASK_TITLES.get(index).size() + (index < MILESTONE_TITLES.size() ? 1 : 0);
            assertThat(elementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                    project.getId(), copiedSections.get(index).getId()))
                    .extracting(element -> element.getSortOrder())
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, expectedElementCount)
                            .map(value -> value * 100).boxed().toList());
        }

        Task copiedTask = copiedTasks.stream()
                .filter(task -> task.getTitle().equals(originalTaskTitle))
                .findFirst().orElseThrow();
        TaskForm update = taskForm(copiedTask);
        update.setTitle("Eigenständig angepasste Aufgabe");
        taskService.updateTask(project.getId(), copiedTask.getId(), update, owner.getId());

        DeleteSectionForm deletion = new DeleteSectionForm();
        deletion.setMode(SectionDeletionMode.DELETE_CONTENT);
        sectionService.deleteSection(project.getId(), copiedSections.getFirst().getId(), deletion, owner.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(taskRepository.findById(originalTaskId)).get()
                .extracting(Task::getTitle).isEqualTo(originalTaskTitle);
        assertThat(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(template.getId())).hasSize(5);
        assertThat(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(project.getId())).hasSize(4);
    }

    @Test
    void normalProjectEditingServicesCannotMutateTemplateRows() {
        User user = saveUser("read-only-template@example.org");
        Template template = saveStaticEventTemplate();
        Task task = template.getElements().stream()
                .filter(Task.class::isInstance).map(Task.class::cast).findFirst().orElseThrow();

        assertThatThrownBy(() -> taskService.updateTask(
                template.getId(), task.getId(), taskForm(task), user.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void malformedTemplateCopyLeavesNoPartialProject() {
        User owner = saveUser("rollback-template@example.org");
        Template foreignTemplate = newTemplate("Fremde Vorlage");
        PlanSection foreignSection = addSection(foreignTemplate, "Fremder Bereich", 100);
        templateRepository.saveAndFlush(foreignTemplate);

        Template malformed = newTemplate("Fehlerhafte Vorlage");
        Task task = newTask("Ungültig zugeordnet", 100);
        malformed.addElement(task);
        task.setPlanSection(foreignSection);
        templateRepository.saveAndFlush(malformed);
        entityManager.clear();
        long projectCount = projectRepository.count();

        assertThatThrownBy(() -> projectService.createProjectFromTemplate(
                malformed.getId(), projectForm(), owner.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("außerhalb der Vorlage");
        assertThat(projectRepository.count()).isEqualTo(projectCount);
    }

    @Test
    void relativeDatesRequireAnExplicitConversionOrIgnoreDecision() {
        User owner = saveUser("relative-template@example.org");
        Template template = newTemplate("Relative Vorlage");
        Task task = newTask("Relativ fällig", 100);
        task.setRelativeDueDay(4);
        template.addElement(task);
        templateRepository.saveAndFlush(template);

        assertThat(templateService.assessRelativeDates(template.getId(), null))
                .satisfies(assessment -> {
                    assertThat(assessment.hasRelativeDates()).isTrue();
                    assertThat(assessment.convertible()).isFalse();
                });
        var ignored = projectService.createProjectFromTemplate(
                template.getId(), projectForm(), owner.getId(), TemplateDateHandling.IGNORE);
        assertThat(taskRepository.findPlanTasks(ignored.getId())).singleElement()
                .extracting(Task::getDueDate).isNull();

        ProjectCreateForm datedForm = projectForm();
        datedForm.setTitle("Datiertes Projekt");
        datedForm.setStartDate(java.time.LocalDate.of(2026, 9, 10));
        var converted = projectService.createProjectFromTemplate(
                template.getId(), datedForm, owner.getId(), TemplateDateHandling.CONVERT);
        assertThat(taskRepository.findPlanTasks(converted.getId())).singleElement()
                .extracting(Task::getDueDate).isEqualTo(java.time.LocalDate.of(2026, 9, 14));
    }

    private Template saveStaticEventTemplate() {
        Template template = newTemplate("Kleine Veranstaltung planen");
        template.setDescription(
                "Vorlage zur Planung einer kleineren privaten, studentischen oder ehrenamtlichen Veranstaltung.");
        for (int sectionIndex = 0; sectionIndex < SECTION_TITLES.size(); sectionIndex++) {
            PlanSection section = addSection(template, SECTION_TITLES.get(sectionIndex), (sectionIndex + 1) * 100);
            List<String> titles = TASK_TITLES.get(sectionIndex);
            for (int taskIndex = 0; taskIndex < titles.size(); taskIndex++) {
                Task task = newTask(titles.get(taskIndex), (taskIndex + 1) * 100);
                template.addElement(task);
                section.addElement(task);
            }
            if (sectionIndex < MILESTONE_TITLES.size()) {
                Milestone milestone = new Milestone();
                milestone.setTitle(MILESTONE_TITLES.get(sectionIndex));
                milestone.setSortOrder((titles.size() + 1) * 100);
                milestone.setOrigin(ElementOrigin.TEMPLATE);
                milestone.setReviewStatus(PlanReviewStatus.UNREVIEWED);
                template.addElement(milestone);
                section.addElement(milestone);
            }
        }
        return templateRepository.saveAndFlush(template);
    }

    private Template newTemplate(String title) {
        Template template = new Template();
        template.setTitle(title);
        template.setCategory(ProjectCategory.EVENT);
        template.setSubcategory(ProjectSubCategory.OTHER_EVENT);
        template.setCollaborationMode(CollaborationMode.BOTH);
        template.setStructureMode(StructureMode.THEMATIC);
        template.setSortMode(SortMode.MANUAL);
        return template;
    }

    private PlanSection addSection(Template template, String title, int sortOrder) {
        PlanSection section = new PlanSection();
        section.setTitle(title);
        section.setSortOrder(sortOrder);
        section.setOrigin(ElementOrigin.TEMPLATE);
        section.setReviewStatus(PlanReviewStatus.UNREVIEWED);
        template.addSection(section);
        return section;
    }

    private Task newTask(String title, int sortOrder) {
        Task task = new Task();
        task.setTitle(title);
        task.setSortOrder(sortOrder);
        task.setPriority(TaskPriority.MEDIUM);
        task.setOrigin(ElementOrigin.TEMPLATE);
        task.setReviewStatus(PlanReviewStatus.UNREVIEWED);
        return task;
    }

    private ProjectCreateForm projectForm() {
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Meine kleine Veranstaltung");
        form.setCreationType(CreationType.TEMPLATE);
        form.setCategory(ProjectCategory.EVENT);
        form.setSubcategory(ProjectSubCategory.OTHER_EVENT);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        return form;
    }

    private TaskForm taskForm(Task task) {
        TaskForm form = new TaskForm();
        form.setTitle(task.getTitle());
        form.setDescription(task.getDescription());
        form.setPlanSectionId(task.getPlanSection() == null ? null : task.getPlanSection().getId());
        form.setSortOrder(task.getSortOrder());
        form.setStatus(task.getStatus());
        form.setPriority(task.getPriority());
        form.setStartDate(task.getStartDate());
        form.setDueDate(task.getDueDate());
        form.setLockVersion(task.getLockVersion());
        return form;
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("$2a$12$test-hash");
        return userRepository.saveAndFlush(user);
    }
}

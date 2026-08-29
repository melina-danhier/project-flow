package de.melinadanhier.projectflow.integration;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.plancontainer.model.PlanContainer;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.user.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class JpaEntityModelTest {

    @Test
    void sectionsHaveNoOwnDateFields() {
        assertThat(java.util.Arrays.stream(PlanSection.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("startDate", "endDate", "relativeStartDay", "relativeEndDay");
        assertThat(java.util.Arrays.stream(DraftSection.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("startDate", "endDate");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void savesAndLoadsUserWithTechnicalFields() {
        User user = newUser("alex@example.org");

        entityManager.persistAndFlush(user);
        UUID id = user.getId();
        entityManager.clear();

        User loaded = entityManager.find(User.class, id);
        assertThat(loaded.getEmail()).isEqualTo("alex@example.org");
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getLockVersion()).isZero();
    }

    @Test
    void rejectsDuplicateEmail() {
        entityManager.persistAndFlush(newUser("same@example.org"));
        entityManager.persist(newUser("same@example.org"));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void persistsProjectAsPlanContainerSubtype() {
        Project project = newProject("Umzug", CreationType.EMPTY, ProjectStatus.ACTIVE);
        project.setCategory(TemplateCategory.HOME);
        project.setSubcategory(ProjectSubCategory.MOVING);

        entityManager.persistAndFlush(project);
        UUID id = project.getId();
        entityManager.clear();

        PlanContainer loaded = entityManager.find(PlanContainer.class, id);
        assertThat(loaded).isInstanceOf(Project.class);
        assertThat(loaded.getTitle()).isEqualTo("Umzug");
        assertThat(((Project) loaded).getSubcategory()).isEqualTo(ProjectSubCategory.MOVING);
        assertThat(jdbcTemplate.queryForObject("select subcategory from projects where id = ?",
                String.class, id)).isEqualTo("MOVING");
    }

    @Test
    void persistsSectionTaskAndMilestoneThroughJoinedInheritance() {
        Project project = newProject("Präsentation", CreationType.AI, ProjectStatus.DRAFT);
        PlanSection section = newSection("Vorbereitung", ElementOrigin.AI, 0);
        Task task = newTask("Folien erstellen", ElementOrigin.AI, 0);
        Milestone milestone = newMilestone("Generalprobe", ElementOrigin.AI, 1);
        project.addSection(section);
        project.addElement(task);
        project.addElement(milestone);
        section.addElement(task);
        section.addElement(milestone);

        entityManager.persistAndFlush(project);
        UUID sectionId = section.getId();
        UUID taskId = task.getId();
        UUID milestoneId = milestone.getId();
        entityManager.clear();

        assertThat(entityManager.find(PlanSection.class, sectionId).getTitle()).isEqualTo("Vorbereitung");
        assertThat(entityManager.find(Task.class, taskId).getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(entityManager.find(Milestone.class, milestoneId).getTitle()).isEqualTo("Generalprobe");
    }

    @Test
    void rejectsDuplicateMembershipForProjectAndUser() {
        User user = newUser("owner@example.org");
        Project project = newProject("Projekt", CreationType.EMPTY, ProjectStatus.ACTIVE);
        entityManager.persist(user);
        entityManager.persist(project);

        entityManager.persistAndFlush(newMembership(project, user));
        entityManager.persist(newMembership(project, user));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void persistsTemplateWithRelativeTaskDates() {
        Template template = new Template();
        template.setTitle("Studienprojekt");
        template.setCategory(TemplateCategory.EDUCATION);
        template.setSubcategory(ProjectSubCategory.PRESENTATION_OR_REPORT);
        template.setRecommendedDurationDays(21);
        template.setCollaborationMode(CollaborationMode.GROUP);
        PlanSection section = newSection("Recherche", ElementOrigin.TEMPLATE, 0);
        Task task = newTask("Quellen sammeln", ElementOrigin.TEMPLATE, 0);
        task.setRelativeStartDay(0);
        task.setRelativeDueDay(5);
        template.addSection(section);
        template.addElement(task);
        section.addElement(task);

        entityManager.persistAndFlush(template);
        UUID id = template.getId();
        entityManager.clear();

        Template loaded = entityManager.find(Template.class, id);
        assertThat(loaded.getSubcategory()).isEqualTo(ProjectSubCategory.PRESENTATION_OR_REPORT);
        assertThat(jdbcTemplate.queryForObject("select subcategory from plan_templates where id = ?",
                String.class, id)).isEqualTo("PRESENTATION_OR_REPORT");
        assertThat(loaded.getRecommendedDurationDays()).isEqualTo(21);
        assertThat(loaded.getSections()).singleElement()
                .extracting(PlanSection::getTitle)
                .isEqualTo("Recherche");
        assertThat(loaded.getElements()).singleElement()
                .isInstanceOf(Task.class)
                .extracting(element -> ((Task) element).getRelativeDueDay())
                .isEqualTo(5);
    }

    @Test
    void persistsPlanDraftAndItsContentsAsSeparateAggregate() {
        Project project = newProject("KI-Projekt", CreationType.AI, ProjectStatus.DRAFT);
        entityManager.persist(project);
        DraftPlan draft = newDraft(project);
        DraftSection section = new DraftSection();
        section.setTitle("Entwurfssection");
        DraftTask task = new DraftTask();
        task.setTitle("Vorschlag prüfen");
        DraftMilestone milestone = new DraftMilestone();
        milestone.setTitle("Plan bestätigt");
        milestone.setSortOrder(1);
        draft.addSection(section);
        draft.addElement(task);
        draft.addElement(milestone);
        section.addElement(task);
        section.addElement(milestone);

        entityManager.persistAndFlush(draft);
        UUID id = draft.getId();
        entityManager.clear();

        DraftPlan loaded = entityManager.find(DraftPlan.class, id);
        assertThat(loaded.getSections()).hasSize(1);
        assertThat(loaded.getElements())
                .hasExactlyElementsOfTypes(DraftTask.class, DraftMilestone.class);
    }

    @Test
    void persistsAndReloadsTaskPrerequisites() {
        Project project = newProject("Abhängigkeiten", CreationType.EMPTY, ProjectStatus.ACTIVE);
        Task predecessor = newTask("Konzept", ElementOrigin.USER, 0);
        Task successor = newTask("Umsetzung", ElementOrigin.USER, 1);
        project.addElement(predecessor);
        project.addElement(successor);
        successor.addPrerequisite(predecessor);

        entityManager.persistAndFlush(project);
        UUID successorId = successor.getId();
        entityManager.clear();

        Task loaded = entityManager.find(Task.class, successorId);
        assertThat(loaded.getPrerequisites()).singleElement()
                .extracting(Task::getTitle)
                .isEqualTo("Konzept");
    }

    @Test
    void persistsAndReloadsDraftTaskPrerequisites() {
        Project project = newProject("Draft-Abhängigkeiten", CreationType.AI, ProjectStatus.DRAFT);
        entityManager.persist(project);
        DraftPlan draft = newDraft(project);
        DraftTask predecessor = new DraftTask();
        predecessor.setTitle("Entwurf Konzept");
        DraftTask successor = new DraftTask();
        successor.setTitle("Entwurf Umsetzung");
        successor.setSortOrder(1);
        draft.addElement(predecessor);
        draft.addElement(successor);
        successor.addPrerequisite(predecessor);

        entityManager.persistAndFlush(draft);
        UUID successorId = successor.getId();
        entityManager.clear();

        DraftTask loaded = entityManager.find(DraftTask.class, successorId);
        assertThat(loaded.getPrerequisites()).singleElement()
                .extracting(DraftTask::getTitle)
                .isEqualTo("Entwurf Konzept");
    }

    @Test
    void storesEnumsAsStrings() {
        Project project = newProject("Enum-Test", CreationType.EMPTY, ProjectStatus.ACTIVE);
        Task task = newTask("Wichtig", ElementOrigin.USER, 0);
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.IN_PROGRESS);
        project.addElement(task);
        entityManager.persistAndFlush(project);

        String status = jdbcTemplate.queryForObject(
                "select status from tasks where id = ?", String.class, task.getId());
        String priority = jdbcTemplate.queryForObject(
                "select priority from tasks where id = ?", String.class, task.getId());
        String origin = jdbcTemplate.queryForObject(
                "select origin from plan_elements where id = ?", String.class, task.getId());

        assertThat(status).isEqualTo("IN_PROGRESS");
        assertThat(priority).isEqualTo("HIGH");
        assertThat(origin).isEqualTo("USER");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void detectsOptimisticLockConflict() {
        User user = newUser("locking@example.org");
        EntityManager setup = entityManagerFactory.createEntityManager();
        setup.getTransaction().begin();
        setup.persist(user);
        setup.getTransaction().commit();
        setup.close();

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        first.getTransaction().begin();
        second.getTransaction().begin();
        User firstCopy = first.find(User.class, user.getId());
        User secondCopy = second.find(User.class, user.getId());
        firstCopy.setDisplayName("Erste Änderung");
        secondCopy.setDisplayName("Zweite Änderung");
        first.getTransaction().commit();

        assertThatThrownBy(second.getTransaction()::commit)
                .isInstanceOf(RollbackException.class);

        first.close();
        second.close();
    }

    @Test
    void taskStatusMaintainsCompletionTimestamp() {
        Task task = new Task();

        task.changeStatus(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();

        task.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$not-a-plain-text-password-hash");
        user.setDisplayName("Test User");
        return user;
    }

    private Project newProject(String title, CreationType creationType, ProjectStatus status) {
        Project project = new Project();
        project.setTitle(title);
        project.setCreationType(creationType);
        project.setStatus(status);
        project.setLocation(status == ProjectStatus.DRAFT ? ProjectLocation.DRAFT : ProjectLocation.OVERVIEW);
        return project;
    }

    private PlanSection newSection(String title, ElementOrigin origin, int sortOrder) {
        PlanSection section = new PlanSection();
        section.setTitle(title);
        section.setOrigin(origin);
        section.setSortOrder(sortOrder);
        return section;
    }

    private Task newTask(String title, ElementOrigin origin, int sortOrder) {
        Task task = new Task();
        task.setTitle(title);
        task.setOrigin(origin);
        task.setSortOrder(sortOrder);
        return task;
    }

    private Milestone newMilestone(String title, ElementOrigin origin, int sortOrder) {
        Milestone milestone = new Milestone();
        milestone.setTitle(title);
        milestone.setOrigin(origin);
        milestone.setSortOrder(sortOrder);
        milestone.setDueDate(LocalDate.now().plusDays(7));
        return milestone;
    }

    private ProjectMember newMembership(Project project, User user) {
        ProjectMember membership = new ProjectMember();
        membership.setProject(project);
        membership.setUser(user);
        membership.setRole(ProjectMemberRole.OWNER);
        return membership;
    }

    private DraftPlan newDraft(Project project) {
        DraftPlan draft = new DraftPlan();
        draft.setProject(project);
        return draft;
    }
}

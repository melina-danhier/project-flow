package de.melinadanhier.projectflow.plancontainer.template;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import de.melinadanhier.projectflow.plancontainer.template.mapper.TemplateMapper;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateRecommendationTest {

    @Test
    void recommendsOnlyTheBestCategoryAndProjectTypeMatchWithoutFilteringTheCatalog() {
        TemplateRepository repository = mock(TemplateRepository.class);
        TemplateMapper mapper = mock(TemplateMapper.class);
        TemplateService service = new TemplateService(
                repository,
                mapper,
                mock(PlanSectionRepository.class),
                mock(PlanElementRepository.class),
                mock(PlanElementMapper.class)
        );
        Template broadMatch = new Template();
        Template exactMatch = new Template();
        Template differentCategory = new Template();
        TemplateSummaryDto broadSummary = summary(ProjectCategory.EDUCATION, ProjectSubCategory.TERM_PAPER);
        TemplateSummaryDto exactSummary = summary(ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);
        TemplateSummaryDto differentSummary = summary(ProjectCategory.EVENT, ProjectSubCategory.STUDY_EVENT);
        when(repository.findAllByActiveTrueOrderByTitleAsc())
                .thenReturn(List.of(broadMatch, exactMatch, differentCategory));
        when(mapper.toSummaryDto(broadMatch)).thenReturn(broadSummary);
        when(mapper.toSummaryDto(exactMatch)).thenReturn(exactSummary);
        when(mapper.toSummaryDto(differentCategory)).thenReturn(differentSummary);

        assertThat(service.getTemplates()).containsExactly(broadSummary, exactSummary, differentSummary);
        assertThat(service.findRecommendation(ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT))
                .get().isSameAs(exactSummary);
        assertThat(service.recommendations(ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT))
                .containsExactly(exactSummary);
    }

    @Test
    void searchesOnlyTitleAndDescriptionCaseInsensitivelyAndKeepsRepositoryOrder() {
        TemplateRepository repository = mock(TemplateRepository.class);
        TemplateMapper mapper = mock(TemplateMapper.class);
        TemplateService service = service(repository, mapper);
        Template alpha = new Template();
        Template beta = new Template();
        Template unrelated = new Template();
        TemplateSummaryDto alphaSummary = summary(ProjectCategory.HOME, ProjectSubCategory.MOVING);
        alphaSummary.setTitle("Alpha Umzug");
        alphaSummary.setDescription("Eine hilfreiche Checkliste");
        TemplateSummaryDto betaSummary = summary(ProjectCategory.EDUCATION, ProjectSubCategory.THESIS);
        betaSummary.setTitle("Beta Arbeit");
        betaSummary.setDescription("UMZUG im Beschreibungstext");
        TemplateSummaryDto unrelatedSummary = summary(ProjectCategory.EVENT, ProjectSubCategory.PRIVATE_CELEBRATION);
        unrelatedSummary.setTitle("Feier");
        unrelatedSummary.setDescription("Gäste planen");
        when(repository.findAllByActiveTrueOrderByTitleAsc()).thenReturn(List.of(alpha, beta, unrelated));
        when(mapper.toSummaryDto(alpha)).thenReturn(alphaSummary);
        when(mapper.toSummaryDto(beta)).thenReturn(betaSummary);
        when(mapper.toSummaryDto(unrelated)).thenReturn(unrelatedSummary);

        assertThat(service.search("umZuG")).containsExactly(alphaSummary, betaSummary);
        assertThat(service.search("hilfreich")).containsExactly(alphaSummary);
        assertThat(service.search("Gäste")).containsExactly(unrelatedSummary);
        assertThat(service.search("EVENT")).isEmpty();
    }

    @Test
    void filtersByTopLevelCategoryAndExposesElementCounts() {
        TemplateRepository repository = mock(TemplateRepository.class);
        TemplateMapper mapper = mock(TemplateMapper.class);
        TemplateService service = service(repository, mapper);
        Template home = new Template();
        var task = new de.melinadanhier.projectflow.planelement.model.Task();
        var milestone = new de.melinadanhier.projectflow.planelement.model.Milestone();
        home.addElement(task);
        home.addElement(milestone);
        Template event = new Template();
        Template uncategorized = new Template();
        TemplateSummaryDto homeSummary = summary(ProjectCategory.HOME, ProjectSubCategory.MOVING);
        homeSummary.setTitle("Alpha");
        TemplateSummaryDto eventSummary = summary(ProjectCategory.EVENT, ProjectSubCategory.PRIVATE_CELEBRATION);
        eventSummary.setTitle("Beta");
        TemplateSummaryDto uncategorizedSummary = summary(null, null);
        uncategorizedSummary.setTitle("Ohne Kategorie");
        when(repository.findAllByActiveTrueOrderByTitleAsc()).thenReturn(List.of(home, event, uncategorized));
        when(mapper.toSummaryDto(home)).thenReturn(homeSummary);
        when(mapper.toSummaryDto(event)).thenReturn(eventSummary);
        when(mapper.toSummaryDto(uncategorized)).thenReturn(uncategorizedSummary);

        assertThat(service.getTemplates(ProjectCategory.HOME)).containsExactly(homeSummary);
        assertThat(homeSummary.getTaskCount()).isEqualTo(1);
        assertThat(homeSummary.getMilestoneCount()).isEqualTo(1);
        assertThat(service.getTemplates(ProjectCategory.OTHER)).containsExactly(uncategorizedSummary);
    }

    private TemplateService service(TemplateRepository repository, TemplateMapper mapper) {
        return new TemplateService(repository, mapper, mock(PlanSectionRepository.class),
                mock(PlanElementRepository.class), mock(PlanElementMapper.class));
    }

    private TemplateSummaryDto summary(ProjectCategory category, ProjectSubCategory subcategory) {
        TemplateSummaryDto summary = new TemplateSummaryDto();
        summary.setId(UUID.randomUUID());
        summary.setCategory(category);
        summary.setSubcategory(subcategory);
        return summary;
    }
}

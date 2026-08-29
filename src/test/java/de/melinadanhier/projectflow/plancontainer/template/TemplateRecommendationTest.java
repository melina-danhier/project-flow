package de.melinadanhier.projectflow.plancontainer.template;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import de.melinadanhier.projectflow.plancontainer.template.mapper.TemplateMapper;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
                mock(TaskRepository.class),
                mock(MilestoneRepository.class),
                mock(PlanElementMapper.class)
        );
        Template broadMatch = new Template();
        Template exactMatch = new Template();
        Template differentCategory = new Template();
        TemplateSummaryDto broadSummary = summary(TemplateCategory.EDUCATION, ProjectSubCategory.TERM_PAPER);
        TemplateSummaryDto exactSummary = summary(TemplateCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT);
        TemplateSummaryDto differentSummary = summary(TemplateCategory.EVENT, ProjectSubCategory.STUDY_EVENT);
        when(repository.findAllByActiveTrueOrderByTitleAsc())
                .thenReturn(List.of(broadMatch, exactMatch, differentCategory));
        when(mapper.toSummaryDto(broadMatch)).thenReturn(broadSummary);
        when(mapper.toSummaryDto(exactMatch)).thenReturn(exactSummary);
        when(mapper.toSummaryDto(differentCategory)).thenReturn(differentSummary);

        assertThat(service.getTemplates()).containsExactly(broadSummary, exactSummary, differentSummary);
        assertThat(service.findRecommendation(TemplateCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT))
                .get().isSameAs(exactSummary);
    }

    private TemplateSummaryDto summary(TemplateCategory category, ProjectSubCategory subcategory) {
        TemplateSummaryDto summary = new TemplateSummaryDto();
        summary.setId(UUID.randomUUID());
        summary.setCategory(category);
        summary.setSubcategory(subcategory);
        return summary;
    }
}

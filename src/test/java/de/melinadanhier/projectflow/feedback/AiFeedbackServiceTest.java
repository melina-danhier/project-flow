package de.melinadanhier.projectflow.feedback;

import de.melinadanhier.projectflow.feedback.domain.*;
import de.melinadanhier.projectflow.feedback.dto.AiFeedbackForm;
import de.melinadanhier.projectflow.feedback.repository.AiFeedbackRepository;
import de.melinadanhier.projectflow.feedback.service.*;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiFeedbackServiceTest {
    @Test
    void ratingUsesTheSmallintTypeCreatedByTheFlywayMigration() throws Exception {
        JdbcTypeCode jdbcType = AiFeedback.class.getDeclaredField("rating")
                .getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcType).isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.SMALLINT);
    }

    @Test
    void storesRatingContextAndTrimmedOptionalComment() {
        AiFeedbackRepository repository = mock(AiFeedbackRepository.class);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        AiFeedbackService service = new AiFeedbackService(repository, Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        AiFeedbackForm form = new AiFeedbackForm();
        form.setRating(5);
        form.setComment("  Sehr hilfreich.  ");

        service.saveIfAbsent(userId,
                new AiFeedbackOpportunity(AiFeedbackContext.PLAN_ADOPTED, actionId, "/projects"), form);

        var captor = org.mockito.ArgumentCaptor.forClass(AiFeedback.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(5);
        assertThat(captor.getValue().getContext()).isEqualTo(AiFeedbackContext.PLAN_ADOPTED);
        assertThat(captor.getValue().getComment()).isEqualTo("Sehr hilfreich.");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(now);
    }

    @Test
    void duplicateConcreteActionIsNotStoredAgain() {
        AiFeedbackRepository repository = mock(AiFeedbackRepository.class);
        AiFeedbackService service = new AiFeedbackService(repository, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(repository.existsByUserIdAndContextAndActionId(
                userId, AiFeedbackContext.AI_EDIT_ADOPTED, actionId)).thenReturn(true);
        AiFeedbackForm form = new AiFeedbackForm();
        form.setRating(3);

        service.saveIfAbsent(userId,
                new AiFeedbackOpportunity(AiFeedbackContext.AI_EDIT_ADOPTED, actionId, "/projects"), form);

        verify(repository, never()).save(any());
    }
}

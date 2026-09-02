package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiWorkflowStatusDtoTest {

    @Test
    void onlyRetryablePlanGenerationFailureCanBeRestartedFromStatusPage() {
        assertThat(status(AiOperation.PLAN_GENERATION).canRetry()).isTrue();
        assertThat(status(AiOperation.PRE_CHECK).canRetry()).isFalse();
    }

    @Test
    void exposesTheUserMessageDefinedByTheStableErrorCode() {
        assertThat(status(AiOperation.PLAN_GENERATION).errorMessage())
                .isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE.getUserMessage());
    }

    private AiWorkflowStatusDto status(AiOperation operation) {
        return new AiWorkflowStatusDto(
                UUID.randomUUID(), UUID.randomUUID(),
                AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE,
                0, 1, 1,
                AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                operation, true, false);
    }
}

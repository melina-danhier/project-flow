package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AiPlanGenerationWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void preCheckTechnicalFailureCannotBeRetriedAsGeneration() {
        var fixture = workflow();

        assertThat(fixture.workflow().expire(fixture.runId(), NOW.plusSeconds(301), error(AiOperation.PRE_CHECK)))
                .isTrue();

        assertThat(fixture.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        assertRunCleared(fixture.workflow());
        assertThatIllegalStateException().isThrownBy(() -> fixture.workflow().startGeneration(
                UUID.randomUUID(), NOW.plusSeconds(600)))
                .withMessageContaining("Vorprüfung");
    }

    @Test
    void matchingGenerationFailureCanStartFreshRun() {
        var fixture = runningGeneration();
        fixture.workflow().recordTechnicalFailure(error(AiOperation.PLAN_GENERATION));
        UUID retryRunId = UUID.randomUUID();

        fixture.workflow().startGeneration(retryRunId, NOW.plusSeconds(600));

        assertThat(fixture.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        assertThat(fixture.workflow().getActiveRunId()).isEqualTo(retryRunId);
        assertThat(fixture.workflow().getRunExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void expirationRequiresMatchingRunAndReachedDeadline() {
        var fixture = workflow();

        assertThat(fixture.workflow().expire(UUID.randomUUID(), NOW.plusSeconds(301), error(AiOperation.PRE_CHECK)))
                .isFalse();
        assertThat(fixture.workflow().expire(fixture.runId(), NOW.plusSeconds(299), error(AiOperation.PRE_CHECK)))
                .isFalse();
        assertThat(fixture.workflow().getActiveRunId()).isEqualTo(fixture.runId());

        assertThat(fixture.workflow().expire(fixture.runId(), NOW.plusSeconds(300), error(AiOperation.PRE_CHECK)))
                .isTrue();
        assertRunCleared(fixture.workflow());
    }

    @Test
    void pendingRunsCanBeCancelledOnlyWithMatchingRun() {
        var preCheck = workflow();
        assertThat(preCheck.workflow().cancelPreCheckRun(UUID.randomUUID())).isFalse();
        assertThat(preCheck.workflow().cancelPreCheckRun(preCheck.runId())).isTrue();
        assertThat(preCheck.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED);
        assertRunCleared(preCheck.workflow());

        var generation = runningGeneration();
        ReflectionTestUtils.setField(generation.workflow(), "status", AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        assertThat(generation.workflow().cancelGenerationRun(generation.runId())).isTrue();
        assertThat(generation.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED);
        assertRunCleared(generation.workflow());
    }

    @Test
    void successfulPreCheckAndGenerationClearTheirRunState() {
        var preCheck = workflow();
        ReflectionTestUtils.setField(preCheck.workflow(), "status", AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        preCheck.workflow().recordPreCheckResult("{}", false);
        assertThat(preCheck.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED);
        assertRunCleared(preCheck.workflow());

        var generation = runningGeneration();
        generation.workflow().recordGenerationCompleted("{}");
        assertThat(generation.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        assertRunCleared(generation.workflow());
    }

    @Test
    void storesCustomOpenPointContextSeparatelyAndDefaultAcceptanceReplacesIt() {
        var fixture = workflow();
        ReflectionTestUtils.setField(fixture.workflow(), "status",
                AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);

        fixture.workflow().confirmOpenPointContext(2, "  Eigene bestätigte Planungsgrundlage.  ");

        assertThat(fixture.workflow().getAcceptedOpenPointIndices()).containsExactly(2);
        assertThat(fixture.workflow().getCustomOpenPointInterpretations())
                .containsEntry(2, "Eigene bestätigte Planungsgrundlage.");

        fixture.workflow().acceptOpenPoint(2);

        assertThat(fixture.workflow().getAcceptedOpenPointIndices()).containsExactly(2);
        assertThat(fixture.workflow().getCustomOpenPointInterpretations()).isEmpty();
    }

    @Test
    void restartingPreCheckClearsPreviousReviewAndBlocksGeneration() {
        var fixture = workflow();
        ReflectionTestUtils.setField(fixture.workflow(), "status",
                AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        ReflectionTestUtils.setField(fixture.workflow(), "preCheckResult", "{\"problems\":[]}");
        fixture.workflow().acceptOpenPoint(0);
        UUID rerunId = UUID.randomUUID();

        fixture.workflow().restartPreCheck("{\"corrected\":true}", rerunId, NOW.plusSeconds(600));

        assertThat(fixture.workflow().getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING);
        assertThat(fixture.workflow().getConfirmedSnapshot()).isEqualTo("{\"corrected\":true}");
        assertThat(fixture.workflow().getPreCheckResult()).isNull();
        assertThat(fixture.workflow().getAcceptedOpenPointIndices()).isEmpty();
        assertThat(fixture.workflow().getActiveRunId()).isEqualTo(rerunId);
        assertThatIllegalStateException().isThrownBy(() -> fixture.workflow().startGeneration(
                UUID.randomUUID(), NOW.plusSeconds(900)));
    }

    private Fixture workflow() {
        UUID runId = UUID.randomUUID();
        var workflow = AiPlanGenerationWorkflow.create(new Project(), "{}", "ai-wizard-v3",
                UUID.randomUUID(), NOW, "v1", runId, NOW.plusSeconds(300));
        return new Fixture(workflow, runId);
    }

    private Fixture runningGeneration() {
        var fixture = workflow();
        ReflectionTestUtils.setField(fixture.workflow(), "status", AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        ReflectionTestUtils.setField(fixture.workflow(), "preCheckResult", "{}");
        return fixture;
    }

    private AiTechnicalError error(AiOperation operation) {
        var cause = new IllegalStateException("Testfehler");
        return new AiTechnicalError(AiTechnicalErrorCode.PROVIDER_TIMEOUT, operation,
                cause.getMessage(), cause);
    }

    private void assertRunCleared(AiPlanGenerationWorkflow workflow) {
        assertThat(workflow.getActiveRunId()).isNull();
        assertThat(workflow.getRunExpiresAt()).isNull();
    }

    private record Fixture(AiPlanGenerationWorkflow workflow, UUID runId) { }
}

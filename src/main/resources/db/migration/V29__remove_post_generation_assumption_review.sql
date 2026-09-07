UPDATE ai_plan_generation_workflows
SET status = 'GENERATION_COMPLETED'
WHERE status = 'ASSUMPTIONS_REVIEW_PENDING';

UPDATE ai_plan_generation_workflows
SET generated_plan = generated_plan - 'criticalAssumptions'
WHERE jsonb_typeof(generated_plan) = 'object';

-- Preserve unfinished workflows created with the previous pre-check schema.
-- Their acknowledged problem indices remain valid because problem ordering is unchanged.
UPDATE ai_plan_generation_workflows
SET pre_check_result = jsonb_build_object(
        'problems',
        COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                    'severity', problem ->> 'severity',
                    'type', CASE
                        WHEN problem ->> 'severity' = 'ERROR' THEN 'CONFLICT'
                        ELSE 'RISK'
                    END,
                    'message', problem ->> 'message',
                    'suggestedUserAction', COALESCE(
                            problem ->> 'suggestedUserAction',
                            problem ->> 'suggestion',
                            ''),
                    'reviewQuestion', CASE
                        WHEN problem ->> 'severity' = 'WARNING' THEN COALESCE(
                                NULLIF(problem ->> 'reviewQuestion', ''),
                                'Welche Planungsgrundlage soll gelten?')
                        ELSE ''
                    END,
                    'acceptedInterpretation', CASE
                        WHEN problem ->> 'severity' = 'WARNING' THEN COALESCE(
                                problem ->> 'acceptedInterpretation',
                                problem ->> 'suggestion',
                                '')
                        ELSE ''
                    END)
                    ORDER BY ordinal)
            FROM jsonb_array_elements(pre_check_result -> 'problems')
                    WITH ORDINALITY AS entries(problem, ordinal)
        ), '[]'::jsonb))
WHERE jsonb_typeof(pre_check_result) = 'object'
  AND pre_check_result ? 'problems';

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_COMPLETED', 'PRE_CHECK_NEEDS_REVIEW', 'PRE_CHECK_CANCELLED',
    'GENERATION_PENDING', 'GENERATION_RUNNING', 'GENERATION_CANCELLED',
    'GENERATION_COMPLETED', 'GENERATION_FAILED', 'TECHNICAL_FAILURE'));

ALTER TABLE ai_plan_generation_workflows
    DROP COLUMN generation_assumption_context,
    DROP COLUMN pending_assumption_review;

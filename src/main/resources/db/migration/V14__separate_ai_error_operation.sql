ALTER TABLE ai_plan_generation_workflows
    DROP CONSTRAINT ck_ai_workflows_technical_error;

ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN last_ai_operation VARCHAR(30);

UPDATE ai_plan_generation_workflows
SET last_ai_operation = CASE
    WHEN pre_check_result IS NULL THEN 'PRE_CHECK'
    ELSE 'PLAN_GENERATION'
END
WHERE last_technical_error IS NOT NULL;

UPDATE ai_plan_generation_workflows
SET last_technical_error = CASE last_technical_error
    WHEN 'INCOMPLETE_AI_RESPONSE' THEN 'INVALID_AI_RESPONSE'
    WHEN 'PRE_CHECK_INITIALIZATION_FAILED' THEN 'UNKNOWN_AI_ERROR'
    WHEN 'PRE_CHECK_PROCESSING_FAILED' THEN 'UNKNOWN_AI_ERROR'
    ELSE last_technical_error
END
WHERE last_technical_error IS NOT NULL;

UPDATE ai_plan_generation_workflows
SET last_error_retryable = CASE
    WHEN last_technical_error IN ('PROVIDER_UNAVAILABLE', 'PROVIDER_TIMEOUT', 'RATE_LIMIT_EXCEEDED')
        THEN TRUE
    ELSE FALSE
END
WHERE last_technical_error IS NOT NULL;

UPDATE ai_plan_generation_workflows
SET last_error_diagnosis = CASE last_technical_error
    WHEN 'PROVIDER_UNAVAILABLE' THEN 'Der KI-Anbieter war vorübergehend nicht erreichbar.'
    WHEN 'PROVIDER_TIMEOUT' THEN 'Der KI-Anbieter hat nicht rechtzeitig geantwortet.'
    WHEN 'RATE_LIMIT_EXCEEDED' THEN 'Das Aufruflimit des KI-Anbieters wurde vorübergehend erreicht.'
    WHEN 'CLIENT_CONFIGURATION_ERROR' THEN 'Der KI-Zugriff ist serverseitig nicht korrekt konfiguriert.'
    WHEN 'INVALID_AI_RESPONSE' THEN 'Die KI-Antwort entsprach nicht den erwarteten Planungsregeln.'
    WHEN 'AI_REFUSAL' THEN 'Der KI-Anbieter hat die unveränderte Anfrage abgelehnt.'
    WHEN 'RETRY_INTERRUPTED' THEN 'Die Wartezeit vor einem erneuten KI-Aufruf wurde unterbrochen.'
    WHEN 'UNKNOWN_AI_ERROR' THEN 'Die KI-Verarbeitung ist an einem internen technischen Fehler gescheitert.'
END
WHERE last_technical_error IS NOT NULL;

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_technical_error CHECK (
        last_technical_error IS NULL OR last_technical_error IN (
            'PROVIDER_UNAVAILABLE', 'PROVIDER_TIMEOUT', 'RATE_LIMIT_EXCEEDED',
            'CLIENT_CONFIGURATION_ERROR', 'INVALID_AI_RESPONSE', 'AI_REFUSAL',
            'RETRY_INTERRUPTED', 'UNKNOWN_AI_ERROR'));

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_error_operation CHECK (
        (last_technical_error IS NULL AND last_ai_operation IS NULL)
        OR (last_technical_error IS NOT NULL AND last_ai_operation IN ('PRE_CHECK', 'PLAN_GENERATION')));

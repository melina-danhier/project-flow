package de.melinadanhier.projectflow.ai.provider.openai;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;

import java.util.List;

public record OpenAiPreCheckOutput(
        List<AiPreCheckProblem> problems
) {}
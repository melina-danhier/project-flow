package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;

import java.util.List;

public record OpenAiPreCheckOutput(List<AiPreCheckProblem> problems) {
}

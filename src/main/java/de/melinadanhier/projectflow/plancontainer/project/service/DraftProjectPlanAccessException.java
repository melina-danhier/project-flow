package de.melinadanhier.projectflow.plancontainer.project.service;

import lombok.Getter;

import java.util.UUID;

@Getter
public class DraftProjectPlanAccessException extends RuntimeException {

    private final UUID projectId;

    public DraftProjectPlanAccessException(UUID projectId) {
        super("Das Projekt befindet sich noch im Entwurfsmodus.");
        this.projectId = projectId;
    }
}

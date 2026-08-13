package de.melinadanhier.projectflow.common.exception;

public class ProjectNotEditableException extends ConflictException {

    public ProjectNotEditableException(String message) {
        super(message);
    }
}

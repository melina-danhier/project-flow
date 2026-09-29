package de.melinadanhier.projectflow.study.service;

public class StudyTaskNotCompletableException extends IllegalStateException {

    public StudyTaskNotCompletableException(String message) {
        super(message);
    }
}

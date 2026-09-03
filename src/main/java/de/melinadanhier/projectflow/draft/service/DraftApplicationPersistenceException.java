package de.melinadanhier.projectflow.draft.service;

public class DraftApplicationPersistenceException extends RuntimeException {

    public DraftApplicationPersistenceException(Throwable cause) {
        super("Der Planentwurf konnte nicht dauerhaft übernommen werden.", cause);
    }
}

package de.melinadanhier.projectflow.common.exception;

import de.melinadanhier.projectflow.common.exception.ConflictException;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException() {
        super("Für diese E-Mail-Adresse besteht bereits ein Konto.");
    }
}

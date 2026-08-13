package com.melina.projectflow.user.exception;

import com.melina.projectflow.common.exception.ConflictException;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException() {
        super("Für diese E-Mail-Adresse besteht bereits ein Konto.");
    }
}

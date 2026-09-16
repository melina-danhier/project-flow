package de.melinadanhier.projectflow.study.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudyConsentForm {

    @AssertTrue(message = "Bitte bestätige die Einwilligung, bevor du die Studie startest.")
    private boolean consent;
}

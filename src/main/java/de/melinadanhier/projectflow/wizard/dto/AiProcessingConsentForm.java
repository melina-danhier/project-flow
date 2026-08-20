package de.melinadanhier.projectflow.wizard.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiProcessingConsentForm {

    @AssertTrue(message = "Bitte stimme der beschriebenen KI-Verarbeitung zu, um fortzufahren.")
    private boolean consent;
}

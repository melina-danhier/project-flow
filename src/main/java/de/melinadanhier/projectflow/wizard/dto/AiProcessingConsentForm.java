package de.melinadanhier.projectflow.wizard.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AiProcessingConsentForm {

    @AssertTrue(message = "Bitte stimme der beschriebenen KI-Verarbeitung zu, um fortzufahren.")
    private boolean consent;

    @NotNull(message = "Der Abschluss dieses Wizards ist nicht mehr gültig. Bitte lade die Zusammenfassung neu.")
    private UUID completionToken;
}

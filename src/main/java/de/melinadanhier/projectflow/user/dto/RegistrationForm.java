package de.melinadanhier.projectflow.user.dto;

import de.melinadanhier.projectflow.common.validation.PasswordsMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@PasswordsMatch
public class RegistrationForm {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String passwordConfirmation;

    @NotBlank
    @Size(min = 1, max = 100)
    private String displayName;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim();
    }
}

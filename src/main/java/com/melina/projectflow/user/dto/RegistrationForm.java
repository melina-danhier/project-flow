package com.melina.projectflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegistrationForm {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    @Size(max = 100)
    private String displayName;
}

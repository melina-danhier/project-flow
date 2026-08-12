package com.melina.projectflow.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UserDto {

    private UUID id;
    private String email;
    private String displayName;
    private boolean enabled;
}

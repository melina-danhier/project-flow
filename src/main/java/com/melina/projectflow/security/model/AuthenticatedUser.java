package com.melina.projectflow.security.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        String passwordHash,
        boolean enabled
) implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("AUTHENTICATED_USER"));

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

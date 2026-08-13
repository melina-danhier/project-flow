package com.melina.projectflow.security;

import com.melina.projectflow.security.model.AuthenticatedUser;
import com.melina.projectflow.security.service.DatabaseUserDetailsService;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DatabaseUserDetailsService service;

    @Test
    void normalizesEmailAndCreatesDedicatedPrincipal() {
        User user = new User();
        user.setEmail("user@example.org");
        user.setPasswordHash("hash");
        user.setDisplayName("User");
        when(userRepository.findByEmail("user@example.org")).thenReturn(Optional.of(user));

        AuthenticatedUser principal = (AuthenticatedUser) service.loadUserByUsername(" USER@Example.ORG ");

        verify(userRepository).findByEmail("user@example.org");
        assertThat(principal.email()).isEqualTo("user@example.org");
        assertThat(principal.passwordHash()).isEqualTo("hash");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("AUTHENTICATED_USER");
    }

    @Test
    void unknownEmailUsesGeneralFailure() {
        when(userRepository.findByEmail("unknown@example.org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.org"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Anmeldung fehlgeschlagen.");
    }
}

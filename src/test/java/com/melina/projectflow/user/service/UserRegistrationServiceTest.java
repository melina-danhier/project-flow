package com.melina.projectflow.user.service;

import com.melina.projectflow.common.exception.DomainValidationException;
import com.melina.projectflow.user.dto.RegistrationForm;
import com.melina.projectflow.user.exception.DuplicateEmailException;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService service;

    @Test
    void registersNormalizedUserWithPasswordHash() {
        RegistrationForm form = form("  Melina  ", "  MELINA@Example.ORG ", "sicheres-passwort");
        when(userRepository.existsByEmail("melina@example.org")).thenReturn(false);
        when(passwordEncoder.encode("sicheres-passwort")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(form);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("Melina");
        assertThat(saved.getEmail()).isEqualTo("melina@example.org");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getPasswordHash()).isNotEqualTo(form.getPassword());
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        RegistrationForm form = form("Melina", " MELINA@example.org ", "sicheres-passwort");
        when(userRepository.existsByEmail("melina@example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.register(form)).isInstanceOf(DuplicateEmailException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejectsDifferentPasswordConfirmation() {
        RegistrationForm form = form("Melina", "melina@example.org", "sicheres-passwort");
        form.setPasswordConfirmation("anderes-passwort");

        assertThatThrownBy(() -> service.register(form)).isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void translatesConcurrentUniqueConstraintViolation() {
        RegistrationForm form = form("Melina", "melina@example.org", "sicheres-passwort");
        when(userRepository.existsByEmail("melina@example.org")).thenReturn(false);
        when(passwordEncoder.encode("sicheres-passwort")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.register(form)).isInstanceOf(DuplicateEmailException.class);
    }

    private RegistrationForm form(String displayName, String email, String password) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName(displayName);
        form.setEmail(email);
        form.setPassword(password);
        form.setPasswordConfirmation(password);
        return form;
    }
}

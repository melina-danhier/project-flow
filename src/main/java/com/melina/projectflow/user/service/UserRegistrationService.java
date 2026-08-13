package com.melina.projectflow.user.service;

import com.melina.projectflow.common.exception.DomainValidationException;
import com.melina.projectflow.user.dto.RegistrationForm;
import com.melina.projectflow.user.exception.DuplicateEmailException;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegistrationForm form) {
        String normalizedEmail = normalizeEmail(form.getEmail());
        if (!Objects.equals(form.getPassword(), form.getPasswordConfirmation())) {
            throw new DomainValidationException("Die Passwörter stimmen nicht überein.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setDisplayName(form.getDisplayName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setEnabled(true);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

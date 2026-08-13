package com.melina.projectflow.security.service;

import com.melina.projectflow.security.model.AuthenticatedUser;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Anmeldung fehlgeschlagen."));
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.isEnabled());
    }
}

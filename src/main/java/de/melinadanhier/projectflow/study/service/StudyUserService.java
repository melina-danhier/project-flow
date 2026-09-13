package de.melinadanhier.projectflow.study.service;

import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyUserService {

    private static final String EMAIL_PREFIX = "study-";
    private static final String EMAIL_SUFFIX = "@projectflow.local";
    private static final String DISPLAY_NAME = "Studienteilnehmer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    @Transactional
    public User getOrCreateStudyUser(String participantId) {
        String email = EMAIL_PREFIX + participantId + EMAIL_SUFFIX;
        return userRepository.findByEmail(email)
                .map(this::ensureStudyDisplayName)
                .orElseGet(() -> createStudyUser(email));
    }

    private User ensureStudyDisplayName(User user) {
        if (!DISPLAY_NAME.equals(user.getDisplayName())) {
            user.setDisplayName(DISPLAY_NAME);
            return userRepository.saveAndFlush(user);
        }
        return user;
    }

    private User createStudyUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(DISPLAY_NAME);
        user.setPasswordHash(
                passwordEncoder.encode(UUID.randomUUID().toString())
        );
        user.setEnabled(true);

        return userRepository.saveAndFlush(user);
    }

    public void login(
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!user.isEnabled()) {
            throw new IllegalStateException("Studienkonto ist deaktiviert.");
        }
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                true,
                DISPLAY_NAME
        );
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, request, response);
    }
}

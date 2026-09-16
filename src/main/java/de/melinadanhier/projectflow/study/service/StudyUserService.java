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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyUserService {

    private static final String DISPLAY_NAME = "Studienteilnehmer";

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;

    @Transactional
    public User createAnonymousStudyUser() {
        User user = new User();
        user.setDisplayName(DISPLAY_NAME);
        user.setEnabled(true);
        user.setStudyAccount(true);

        return userRepository.saveAndFlush(user);
    }

    public void login(
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!user.isEnabled() || !user.isStudyAccount()) {
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

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}

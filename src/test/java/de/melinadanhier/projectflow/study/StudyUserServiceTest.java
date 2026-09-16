package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudyUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final StudyUserService service = new StudyUserService(userRepository, securityContextRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAnonymousAccountWithoutCredentialsOrProfileData() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.createAnonymousStudyUser();

        assertThat(result.getEmail()).isNull();
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.getDisplayName()).isEqualTo("Studienteilnehmer");
        assertThat(result.isStudyAccount()).isTrue();
        verify(userRepository, times(1)).saveAndFlush(result);
    }

    @Test
    void automaticLoginUsesAnonymousStudyPrincipal() {
        User user = studyUser();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.login(user, request, response);

        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.displayName()).isEqualTo("Studienteilnehmer");
        assertThat(principal.email()).isNull();
        assertThat(principal.passwordHash()).isNull();
        verify(securityContextRepository).saveContext(
                SecurityContextHolder.getContext(), request, response
        );
    }

    @Test
    void logoutClearsAutomaticAuthentication() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityContextHolder.getContext().setAuthentication(mock(
                org.springframework.security.core.Authentication.class));

        service.logout(request, response);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityContextRepository).saveContext(
                SecurityContextHolder.getContext(), request, response
        );
    }

    private User studyUser() {
        User user = new User();
        user.setDisplayName("Studienteilnehmer");
        user.setEnabled(true);
        user.setStudyAccount(true);
        return user;
    }
}

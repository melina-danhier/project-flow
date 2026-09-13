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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final StudyUserService service = new StudyUserService(
            userRepository, passwordEncoder, securityContextRepository
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresFixedDisplayNameForExistingStudyAccount() {
        User existing = studyUser("Alter Anzeigename");
        when(userRepository.findByEmail("study-P-17@projectflow.local"))
                .thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(existing)).thenReturn(existing);

        User result = service.getOrCreateStudyUser("P-17");

        assertThat(result.getDisplayName()).isEqualTo("Studienteilnehmer");
        verify(userRepository).saveAndFlush(existing);
    }

    @Test
    void automaticLoginUsesFixedDisplayNameInPrincipal() {
        User user = studyUser("Studienteilnehmer");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.login(user, request, response);

        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.displayName()).isEqualTo("Studienteilnehmer");
        verify(securityContextRepository).saveContext(
                SecurityContextHolder.getContext(), request, response
        );
    }

    private User studyUser(String displayName) {
        User user = new User();
        user.setEmail("study-P-17@projectflow.local");
        user.setPasswordHash("encoded-password");
        user.setDisplayName(displayName);
        user.setEnabled(true);
        return user;
    }
}

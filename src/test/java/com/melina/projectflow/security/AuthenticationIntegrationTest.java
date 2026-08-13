package com.melina.projectflow.security;

import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void anonymousRequestIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginCreatesAuthenticatedSessionAndLogoutInvalidatesIt() throws Exception {
        String email = "session@example.org";
        saveUser(email, "richtiges-passwort", true);

        MvcResult login = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", "richtiges-passwort")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"))
                .andExpect(authenticated().withUsername(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unknownEmailWrongPasswordAndDisabledUserHaveSameExternalFailure() throws Exception {
        saveUser("wrong-password@example.org", "richtiges-passwort", true);
        saveUser("disabled@example.org", "richtiges-passwort", false);

        assertLoginFailure("unknown@example.org", "irgendein-passwort");
        assertLoginFailure("wrong-password@example.org", "falsches-passwort");
        assertLoginFailure("disabled@example.org", "richtiges-passwort");
    }

    @Test
    void registrationRequiresCsrfAndValidConfirmation() throws Exception {
        mockMvc.perform(post("/register")
                        .param("displayName", "CSRF Test")
                        .param("email", "csrf@example.org")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "sicheres-passwort"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register")
                        .param("displayName", "Validation Test")
                        .param("email", "validation@example.org")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "anderes-passwort")
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(userRepository.existsByEmail("validation@example.org")).isFalse();
    }

    @Test
    void validRegistrationStoresVerifiableBcryptHash() throws Exception {
        String email = "register@example.org";
        mockMvc.perform(post("/register")
                        .param("displayName", "  Register User  ")
                        .param("email", "  REGISTER@Example.ORG ")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "sicheres-passwort")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("Register User");
        assertThat(saved.getPasswordHash()).isNotEqualTo("sicheres-passwort");
        assertThat(passwordEncoder.matches("sicheres-passwort", saved.getPasswordHash())).isTrue();
    }

    private void assertLoginFailure(String email, String password) throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    private User saveUser(String email, String password, boolean enabled) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Security Test");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(enabled);
        return userRepository.saveAndFlush(user);
    }
}

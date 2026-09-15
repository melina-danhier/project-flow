package de.melinadanhier.projectflow.security.config;

import de.melinadanhier.projectflow.security.filter.StudyModeRestrictionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.LogoutFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .addFilterBefore(
                        new StudyModeRestrictionFilter(),
                        LogoutFilter.class
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/ping",
                                "/login",
                                "/register",
                                "/study/start",
                                "/study/continue",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/templates", "/templates/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/projects")
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .build();
    }
}

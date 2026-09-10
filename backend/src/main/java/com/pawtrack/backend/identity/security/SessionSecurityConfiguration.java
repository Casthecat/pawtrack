package com.pawtrack.backend.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.identity.api.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import java.io.IOException;
import java.util.Map;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionSecurityConfiguration {
    @Bean
    SecurityFilterChain sessionSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        var csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookiePath("/");
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        var authEndpoints = new AntPathRequestMatcher("/api/auth/**");
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/api/auth/me").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).requireCsrfProtectionMatcher(request -> {
                    if (!CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) return false;
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    // P4.1 only: anonymous business writes retain the existing demo contract.
                    // All auth writes and all authenticated writes require a CSRF token.
                    return authEndpoints.matches(request) || (authentication != null
                            && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken));
                }))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                message(json, response, 401, "Authentication required."))
                        .accessDeniedHandler((request, response, error) ->
                                message(json, response, 403, "Request rejected. Refresh the CSRF token and retry.")))
                .formLogin(login -> login.loginPage("/api/auth/login").loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json");
                            json.writeValue(response.getOutputStream(), CurrentUserResponse.from((AccountPrincipal) authentication.getPrincipal()));
                        })
                        .failureHandler((request, response, error) ->
                                message(json, response, 401, "Invalid email or password.")))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    private static void message(ObjectMapper json, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), Map.of("message", message));
    }
}

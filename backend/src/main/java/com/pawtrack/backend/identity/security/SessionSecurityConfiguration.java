package com.pawtrack.backend.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.identity.api.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CsrfException;
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
    private static final String[] PUBLIC_READS = {"/api/cats", "/api/cats/{id:[0-9]+}",
            "/api/cats/{id:[0-9]+}/dashboard", "/api/auth/csrf", "/uploads/**", "/actuator/health"};
    private static final String[] AUTHENTICATED_READS = {"/api/auth/me", "/api/adoptions/{id:[0-9]+}"};
    private static final String[] STAFF_READS = {"/api/adoptions", "/api/alerts", "/api/cats/{id:[0-9]+}/health-timeline",
            "/api/cats/{id:[0-9]+}/health", "/api/cats/{id:[0-9]+}/health/alerts", "/api/cats/{id:[0-9]+}/alerts"};

    @Bean
    SecurityFilterChain sessionSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        var csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookiePath("/");
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        var authEndpoints = new AntPathRequestMatcher("/api/auth/**");
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, PUBLIC_READS).permitAll()
                        .requestMatchers(HttpMethod.HEAD, PUBLIC_READS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, AUTHENTICATED_READS).authenticated()
                        .requestMatchers(HttpMethod.HEAD, AUTHENTICATED_READS).authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/adoptions").hasRole("ADOPTER")
                        .requestMatchers(HttpMethod.HEAD, "/api/me/adoptions").hasRole("ADOPTER")
                        .requestMatchers(HttpMethod.POST, "/api/adoptions").hasRole("ADOPTER")
                        .requestMatchers(HttpMethod.GET, STAFF_READS).hasRole("STAFF")
                        .requestMatchers(HttpMethod.HEAD, STAFF_READS).hasRole("STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/cats", "/api/cats/{id:[0-9]+}/health",
                                "/api/cats/{id:[0-9]+}/upload-image").hasRole("STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/adoptions/{id:[0-9]+}/approve",
                                "/api/adoptions/{id:[0-9]+}/reject", "/api/alerts/{id:[0-9]+}/resolve").hasRole("STAFF")
                        .requestMatchers("/api", "/api/**").denyAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").hasRole("STAFF")
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).requireCsrfProtectionMatcher(request -> {
                    if (!CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) return false;
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    // Authorization rejects anonymous business writes; preserve P4.1 CSRF semantics.
                    // All auth writes and all authenticated writes require a CSRF token.
                    return authEndpoints.matches(request) || (authentication != null
                            && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken));
                }))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                message(json, response, 401, "Authentication required."))
                        .accessDeniedHandler((request, response, error) ->
                                message(json, response, 403, error instanceof CsrfException
                                        ? "Request rejected. Refresh the CSRF token and retry."
                                        : (request.getRequestURI().equals("/api/me/adoptions")
                                            || (request.getRequestURI().equals("/api/adoptions") && request.getMethod().equals("POST")))
                                            ? "Adopter access required." : "Staff access required.")))
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

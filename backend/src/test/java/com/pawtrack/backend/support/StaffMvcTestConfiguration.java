package com.pawtrack.backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Domain regression fixtures run as STAFF with CSRF; authorization matrix tests use real logins separately.
@TestConfiguration
public class StaffMvcTestConfiguration {
    public static MockHttpServletRequestBuilder staffRequest() {
        return get("/").with(user(TestAccounts.staff())).with(csrf());
    }
    @Bean MockMvcBuilderCustomizer staffRequests() { return builder -> builder.defaultRequest(staffRequest()); }
}

package com.pawtrack.backend.identity.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deny-probe;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext
@Import(ApiDefaultDenyIntegrationTest.Probe.class)
class ApiDefaultDenyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Probe probe;

    // TestComponent prevents accidental component scanning into other tests or production.
    @TestComponent @RestController
    static class Probe {
        final AtomicInteger invoked = new AtomicInteger();
        @RequestMapping(path = {"/api/unclassified-probe", "/api/cats/private-probe", "/api/auth/private-probe"}, method = {RequestMethod.GET, RequestMethod.POST})
        String unclassified() { invoked.incrementAndGet(); return "private probe payload"; }
        @GetMapping("/staff/test-shell") String shell() { return "public shell"; }
    }

    @ParameterizedTest @ValueSource(strings = {"ANONYMOUS", "ADOPTER", "STAFF"})
    void newUnclassifiedApisAreDeniedEvenForStaff(String role) throws Exception {
        int expected = role.equals("ANONYMOUS") ? 401 : 403;
        for (String path : new String[]{"/api/unclassified-probe", "/api/cats/private-probe", "/api/auth/private-probe"}) {
            for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{get(path), post(path).with(csrf())}) {
                if (!role.equals("ANONYMOUS")) request.with(user("probe").roles(role));
                mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.message").isNotEmpty());
            }
        }
        assertEquals(0, probe.invoked.get());
        mvc.perform(get("/staff/test-shell")).andExpect(status().isOk()).andExpect(content().string("public shell"));
        mvc.perform(get("/api/cats")).andExpect(status().isOk());
    }
}

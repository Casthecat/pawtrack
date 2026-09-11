package com.pawtrack.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:spa-routing",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class SpaRoutingIntegrationTest {
    @Autowired MockMvc mvc;

    @ParameterizedTest @ValueSource(strings = {"/index.html", "/cats/1", "/login", "/applications", "/applications/1", "/staff/care", "/unknown-client-route"})
    void clientRoutesServeStaticShell(String path) throws Exception {
        mvc.perform(get(path).accept("text/html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("pawtrack-static-test-shell")));
    }

    @Test void assetAndApiRequestsKeepTheirOwnRepresentations() throws Exception {
        mvc.perform(get("/assets/probe.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("staticAssetProbe")));
        mvc.perform(get("/api/cats")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/unclassified").accept("text/html")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @ParameterizedTest @ValueSource(strings = {"/uploads/missing.png", "/assets/missing.js", "/missing.png"})
    void missingMediaAndAssetsNeverReceiveTheShell(String path) throws Exception {
        mvc.perform(get(path).accept("text/html")).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("pawtrack-static-test-shell"))));
    }
}

package com.certpractice.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.certpractice.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Health responses are public probes. They must confirm liveness without
 * exposing datasource URLs, credentials, provider secrets or component details.
 */
class ObservabilityHealthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicHealthEndpointIsAnswerAndConfigurationFree() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("cert-practice-java"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void actuatorHealthHidesComponentAndDatasourceDetails() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(
                "components", "jdbc", "postgres", "cert_practice", "password", "datasource");
    }
}

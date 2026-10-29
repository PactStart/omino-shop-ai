package com.omino.shopai.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSessionTest {

    @Test
    void usersRequireASessionAndHealthDoesNot() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .addFilters(new AdminSessionFilter(new ObjectMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        mvc.perform(get("/v1/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(content().string(not(containsString("access_token"))));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @RestController
    static class Probe {
        @GetMapping("/v1/users")
        Map<String, Object> users() {
            return Map.of("list", java.util.List.of());
        }

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }
}

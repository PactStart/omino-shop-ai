package com.omino.shopai.app.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTokenFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InternalTokenFilter filter = new InternalTokenFilter("expected-token", objectMapper);

    @Test
    void missingTokenIs40100() throws Exception {
        MockHttpServletResponse response = invoke(new MockHttpServletRequest("GET", "/api/app/internal/v1/me"));

        assertEquals(401, response.getStatus());
        assertEquals(40100, body(response).get("code").asInt());
    }

    @Test
    void wrongTokenIs40100() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/internal/v1/me");
        request.addHeader("X-Internal-Token", "nope");
        request.addHeader("X-User-Id", "u1");

        MockHttpServletResponse response = invoke(request);

        assertEquals(401, response.getStatus());
        assertEquals(40100, body(response).get("code").asInt());
    }

    @Test
    void missingUserIdIs40100() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/internal/v1/me");
        request.addHeader("X-Internal-Token", "expected-token");

        MockHttpServletResponse response = invoke(request);

        assertEquals(401, response.getStatus());
        assertEquals(40100, body(response).get("code").asInt());
    }

    @Test
    void validHeadersContinue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/internal/v1/me");
        request.addHeader("X-Internal-Token", "expected-token");
        request.addHeader("X-User-Id", "u1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));

        assertEquals(204, response.getStatus());
    }

    @Test
    void publicPathIsNotChecked() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("passed");
        }));
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("rejected request must not continue");
        });
        return response;
    }

    private JsonNode body(MockHttpServletResponse response) throws IOException {
        return objectMapper.readTree(response.getContentAsString());
    }
}

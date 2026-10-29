package com.omino.shopai.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.security.AppJwtFilter;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import com.omino.shopai.app.service.auth.LoginSession;
import com.omino.shopai.app.service.auth.SmsAuthService;
import com.omino.shopai.app.web.app.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmsLoginTest {

    private MemoryAccounts accounts;
    private SmsAuthService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        AppJwtIssuer tokens = new AppJwtIssuer("0123456789abcdef0123456789abcdef", 1800);
        service = new SmsAuthService(accounts, accounts, accounts, tokens, "local", "mock");
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service), new PrivateProbe())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new AppJwtFilter(tokens, new ObjectMapper()))
                .build();
    }

    @Test
    void mockCodeIssuesToken() throws Exception {
        mvc.perform(post("/v1/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\",\"code\":\"888888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void wrongCodeIssuesNothing() {
        assertThrows(ApiBusinessException.class, () -> service.login("13800000001", "000000", null));
        assertTrue(accounts.findActiveByPhone("13800000001").isEmpty());
    }

    @Test
    void missingCredentialIs40100() throws Exception {
        mvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void issuedTokenOpensPrivateRoute() throws Exception {
        LoginSession session = service.login("13800000002", "888888", null);
        assertFalse(session.accessToken().isBlank());
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void prodRejectsMockProvider() {
        SmsAuthService prod = new SmsAuthService(
                accounts, accounts, accounts,
                new AppJwtIssuer("0123456789abcdef0123456789abcdef", 1800),
                "prod", "mock");
        assertThrows(IllegalStateException.class, () -> prod.login("13800000003", "888888", null));
        assertTrue(accounts.findActiveByPhone("13800000003").isEmpty());
    }

    @RestController
    static class PrivateProbe {
        @GetMapping("/v1/me")
        Map<String, String> me() {
            return Map.of("ok", "1");
        }
    }
}

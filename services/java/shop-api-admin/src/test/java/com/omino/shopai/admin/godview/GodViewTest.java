package com.omino.shopai.admin.godview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.omino.shopai.admin.security.AdminSessionFilter;
import com.omino.shopai.admin.security.GodViewIssuer;
import com.omino.shopai.admin.service.godview.IAdminAudit;
import com.omino.shopai.admin.service.godview.AdminGodViewService;
import com.omino.shopai.admin.web.AdminGodViewController;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.entity.AdminAuditLog;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class GodViewTest {

    @Test
    void disabledGodViewIs40303AndEnabledTicketExpiresWithinAnHour() throws Exception {
        List<AdminAuditLog> audit = new ArrayList<>();
        var denied = mvc(new AdminGodViewService(new GodViewIssuer(secret()), audit::add, false));
        denied.perform(post("/v1/god-view/tokens")
                        .sessionAttr("ssoSub", "ops")
                        .cookie(new Cookie("sid_admin", "session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"01USER0000000000000000000A\"}"))
                .andExpect(jsonPath("$.code").value(40303));
        assertEquals(0, audit.size());

        var allowed = mvc(new AdminGodViewService(new GodViewIssuer(secret()), audit::add, true));
        String body = allowed.perform(post("/v1/god-view/tokens")
                        .sessionAttr("ssoSub", "ops")
                        .cookie(new Cookie("sid_admin", "session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"01USER0000000000000000000A\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(body).get("data").get("token").asText();
        SignedJWT jwt = SignedJWT.parse(token);
        assertEquals(1L, jwt.getJWTClaimsSet().getLongClaim("gv"));
        long ttl = jwt.getJWTClaimsSet().getExpirationTime().getTime() - jwt.getJWTClaimsSet().getIssueTime().getTime();
        assertTrue(ttl <= 3_600_000L);
        assertEquals(1, audit.size());
        assertEquals("god_view.issue", audit.get(0).getAction());
    }

    private static org.springframework.test.web.servlet.MockMvc mvc(AdminGodViewService service) {
        return MockMvcBuilders.standaloneSetup(new AdminGodViewController(service))
                .addFilters(new AdminSessionFilter(new ObjectMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static String secret() {
        return "0123456789abcdef0123456789abcdef";
    }
}

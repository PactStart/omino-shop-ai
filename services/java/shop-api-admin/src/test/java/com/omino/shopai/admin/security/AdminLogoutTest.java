package com.omino.shopai.admin.security;

import com.omino.shopai.admin.web.AdminAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLogoutTest {

    @Test
    void logoutExpiresTheSessionCookieAndOpensMyApps() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                new AdminAuthController("http://127.0.0.1:5183", "http://localhost:5173/login"))
                .build();
        mvc.perform(post("/api/admin/v1/auth/logout").contextPath("/api/admin").sessionAttr("ssoSub", "ops"))
                .andExpect(status().isFound())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/admin")))
                .andExpect(header().string("Location", "http://localhost:5173/account/apps"))
                .andExpect(header().string("Location", not(containsString("/logout"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void logoutWithoutPortalUrlDoesNotEndTheIdentitySession() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AdminAuthController("http://127.0.0.1:5183", ""))
                .build();
        mvc.perform(post("/api/admin/v1/auth/logout").contextPath("/api/admin").sessionAttr("ssoSub", "ops"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Location"));
    }
}

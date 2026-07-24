package com.fattorestreet.sec_api.config;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fattorestreet.sec_api.controller.IwbReferenceHoldingsController;
import com.fattorestreet.sec_api.index.IwbHoldingsTickerSet;
import com.fattorestreet.sec_api.testsupport.TestJwtTokens;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix for {@link SecurityConfig}: public routes are anonymous, /admin/** requires a
 * valid HS256 JWT whose user_id claim is the Django admin (1). Sliced on the smallest controller;
 * /admin/** requests are decided by the filter chain before any handler mapping, so an
 * authenticated admin request lands on 404 here (no AdminController in the slice) — which proves
 * authorization passed.
 */
@WebMvcTest(IwbReferenceHoldingsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "SECRET_KEY=test-jwt-signing-secret-32chars-min!") // pragma: allowlist secret
class SecurityConfigTest {

    private static final String JWT_TEST_SECRET = "test-jwt-signing-secret-32chars-min!"; // pragma: allowlist secret

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IwbHoldingsTickerSet iwbHoldingsTickerSet;

    @Test
    void publicEndpoint_noToken_permitted() throws Exception {
        when(iwbHoldingsTickerSet.equityRows()).thenReturn(List.of());

        mockMvc.perform(get("/iwb-reference-holdings"))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoute_noToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/asset-load"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoute_expiredAdminToken_returns401() throws Exception {
        // Well past the resource server's default 60s clock skew.
        String expired = TestJwtTokens.accessToken(
                JWT_TEST_SECRET, 1L, new Date(System.currentTimeMillis() - 7_200_000L));

        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoute_wrongSignature_returns401() throws Exception {
        String forged = TestJwtTokens.accessToken("attacker-secret-that-is-32-chars-xx", 1L);

        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoute_malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoute_nonAdminUserId_returns403() throws Exception {
        String nonAdmin = TestJwtTokens.accessToken(JWT_TEST_SECRET, 2L);

        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoute_adminToken_passesAuthorization() throws Exception {
        String admin = TestJwtTokens.accessToken(JWT_TEST_SECRET, 1L);

        // No AdminController in this slice: 404 (not 401/403) means the filter chain let it through.
        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}

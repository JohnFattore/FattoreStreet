package com.fattorestreet.sec_api.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fattorestreet.sec_api.controller.IwbReferenceHoldingsController;
import com.fattorestreet.sec_api.index.IwbHoldingsTickerSet;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix for {@link SecurityConfig} after the admin routes were retired: every route is
 * public, no route requires a token, and {@code /admin/**} is simply gone.
 *
 * <p>The 404s below are the point. They used to be 401/403 -- a request rejected by the filter
 * chain -- and are now "no such handler", which is the observable difference between a route that
 * is protected and a route that does not exist. Deliberately no {@code SECRET_KEY} property is set
 * on this slice: startup succeeding without it is what proves the shared-secret dependency is gone.
 */
@WebMvcTest(IwbReferenceHoldingsController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    /**
     * Shape-valid HS256 JWT. Never verified now -- it is here to prove that presenting a token
     * changes nothing, rather than to authenticate anything.
     */
    private static final String BEARER_TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            + ".eyJ1c2VyX2lkIjoxfQ.c2lnbmF0dXJl"; // pragma: allowlist secret

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IwbHoldingsTickerSet iwbHoldingsTickerSet;

    @Test
    void publicEndpointNoTokenPermitted() throws Exception {
        when(iwbHoldingsTickerSet.equityRows()).thenReturn(List.of());

        mockMvc.perform(get("/iwb-reference-holdings"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpointWithAStrayBearerTokenStillPermitted() throws Exception {
        // Nothing inspects the header any more, so a leftover token from an old client is inert
        // rather than a 401.
        when(iwbHoldingsTickerSet.equityRows()).thenReturn(List.of());

        mockMvc.perform(get("/iwb-reference-holdings").header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void adminRouteNoTokenReturns404() throws Exception {
        mockMvc.perform(get("/admin/asset-load"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminRouteWithBearerTokenReturns404() throws Exception {
        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminRouteWithGarbageTokenReturns404() throws Exception {
        // Previously 401 from the resource server rejecting the token; now nothing parses it.
        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retiredAdminPostRoutesAreAlsoGone() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/admin/indexes/rebuild"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unmappedRouteIs404NotUnauthorized() throws Exception {
        mockMvc.perform(get("/no-such-route"))
                .andExpect(status().isNotFound());
    }
}

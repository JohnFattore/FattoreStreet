package com.fattorestreet.sec_api.listing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetLoadRunnerTest {

    private static final Map<String, Object> ENRICHMENT = Map.of(
            "fundListingsTotal", 3,
            "resolved", 3,
            "unresolved", 0,
            "updated", 2,
            "skipped", 1,
            "secMfTickerRows", 3,
            "unresolvedTickersSample", List.of());

    @Mock
    private SecTickerLoadService secTickerLoadService;

    @Mock
    private EtfIdentityService etfIdentityService;

    @Mock
    private ConfigurableApplicationContext context;

    private AssetLoadRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AssetLoadRunner(secTickerLoadService, etfIdentityService, context);
        ReflectionTestUtils.setField(runner, "overwriteExisting", false);
    }

    @Test
    void returnsZeroWhenTickersLoadAndEnrichmentSucceeds() {
        when(secTickerLoadService.load())
                .thenReturn(new SecTickerLoadService.SecTickerLoadResult(9000, 3000));
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(ENRICHMENT);

        assertEquals(0, runner.runLoad());
        verify(etfIdentityService).enrichFundListingIdentities(false);
    }

    @Test
    void returnsOneAndSkipsEnrichmentWhenZeroTickersLoaded() {
        // The missing-SEC_CONTACT_EMAIL failure mode: every SEC request 403s, nothing persists, and
        // without this guard the task would exit 0 and look healthy.
        when(secTickerLoadService.load())
                .thenReturn(new SecTickerLoadService.SecTickerLoadResult(0, 0));

        assertEquals(1, runner.runLoad());
        verify(etfIdentityService, never()).enrichFundListingIdentities(anyBoolean());
    }

    @Test
    void returnsOneWhenSecLoadThrows() {
        when(secTickerLoadService.load()).thenThrow(new IllegalStateException("SEC 403"));

        assertEquals(1, runner.runLoad());
        verify(etfIdentityService, never()).enrichFundListingIdentities(anyBoolean());
    }

    @Test
    void returnsOneWhenEnrichmentThrows() {
        when(secTickerLoadService.load())
                .thenReturn(new SecTickerLoadService.SecTickerLoadResult(9000, 3000));
        when(etfIdentityService.enrichFundListingIdentities(false))
                .thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
    }

    @Test
    void passesOverwriteExistingThroughToEnrichment() {
        ReflectionTestUtils.setField(runner, "overwriteExisting", true);
        when(secTickerLoadService.load())
                .thenReturn(new SecTickerLoadService.SecTickerLoadResult(1, 1));
        when(etfIdentityService.enrichFundListingIdentities(true)).thenReturn(ENRICHMENT);

        assertEquals(0, runner.runLoad());
        verify(etfIdentityService).enrichFundListingIdentities(true);
    }

    @Test
    void countsFundOnlyLoadAsWorkDone() {
        when(secTickerLoadService.load())
                .thenReturn(new SecTickerLoadService.SecTickerLoadResult(0, 12));
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(ENRICHMENT);

        assertEquals(0, runner.runLoad());
    }
}

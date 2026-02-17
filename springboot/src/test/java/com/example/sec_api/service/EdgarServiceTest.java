package com.example.sec_api.service;

import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EdgarServiceTest {

    @Mock
    private WebService webService;

    @Mock
    private QuarterService quarterService;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private EdgarService edgarService;

    private void callDeriveBalanceSheetFields(Quarter q) throws Exception {
        Method method = EdgarService.class.getDeclaredMethod("deriveBalanceSheetFields", Quarter.class);
        method.setAccessible(true);
        method.invoke(edgarService, q);
    }

    @Test
    void derivesEquity_whenAssetsAndLiabilitiesPresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setLiabilities(600L);

        callDeriveBalanceSheetFields(q);

        assertEquals(400L, q.getStockholdersEquity());
        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
    }

    @Test
    void derivesAssets_whenLiabilitiesAndEquityPresent() throws Exception {
        Quarter q = new Quarter();
        q.setLiabilities(600L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void derivesLiabilities_whenAssetsAndEquityPresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void noOp_whenAllThreePresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setLiabilities(600L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void noOp_whenOnlyOnePresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertNull(q.getLiabilities());
        assertNull(q.getStockholdersEquity());
    }

    @Test
    void noOp_whenNonePresent() throws Exception {
        Quarter q = new Quarter();

        callDeriveBalanceSheetFields(q);

        assertNull(q.getAssets());
        assertNull(q.getLiabilities());
        assertNull(q.getStockholdersEquity());
    }

    @Test
    void handlesNegativeEquity() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(500L);
        q.setLiabilities(800L);

        callDeriveBalanceSheetFields(q);

        assertEquals(-300L, q.getStockholdersEquity());
    }
}

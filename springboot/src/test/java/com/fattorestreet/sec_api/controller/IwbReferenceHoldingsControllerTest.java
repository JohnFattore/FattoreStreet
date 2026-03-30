package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.index.IwbHoldingsCsv;
import com.fattorestreet.sec_api.index.IwbHoldingsTickerSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IwbReferenceHoldingsController.class)
class IwbReferenceHoldingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IwbHoldingsTickerSet iwbHoldingsTickerSet;

    @Test
    void listIwbReferenceHoldings_returnsRows() throws Exception {
        when(iwbHoldingsTickerSet.equityRows()).thenReturn(List.of(
                new IwbHoldingsCsv.IwbEquityRow("NVDA", new BigDecimal("6.71")),
                new IwbHoldingsCsv.IwbEquityRow("AAPL", new BigDecimal("6.13"))));

        mockMvc.perform(get("/iwb-reference-holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticker").value("NVDA"))
                .andExpect(jsonPath("$[0].weightPercent").value(6.71))
                .andExpect(jsonPath("$[1].ticker").value("AAPL"))
                .andExpect(jsonPath("$[1].weightPercent").value(6.13));
    }
}

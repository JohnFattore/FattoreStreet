package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.index.IndexMemberApiService;
import com.fattorestreet.sec_api.index.IndexMemberApiService.IndexMemberRow;
import com.fattorestreet.sec_api.index.IndexMemberApiService.StockRow;
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

@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexMemberApiService indexMemberApiService;

    @Test
    void listIndexMembers_returns200() throws Exception {
        StockRow stock = new StockRow(
                "AAPL", "Apple", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, "US", "United States", "Common Stock", 1980);
        when(indexMemberApiService.listAll()).thenReturn(List.of(
                new IndexMemberRow(1L, new BigDecimal("5.5"), "Test Index", false, "", stock)
        ));
        mockMvc.perform(get("/index-members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].index").value("Test Index"))
                .andExpect(jsonPath("$[0].stock.ticker").value("AAPL"));
    }

    @Test
    void listIndexMembers_withCode_filters() throws Exception {
        StockRow stock = new StockRow(
                "MSFT", "Microsoft", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, "US", "United States", "Common Stock", 1986);
        when(indexMemberApiService.listByIndexCode("FAT50")).thenReturn(List.of(
                new IndexMemberRow(2L, new BigDecimal("3.25"), "Fattore 50", false, "", stock)
        ));
        mockMvc.perform(get("/index-members").param("code", "FAT50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].index").value("Fattore 50"))
                .andExpect(jsonPath("$[0].stock.ticker").value("MSFT"));
    }
}

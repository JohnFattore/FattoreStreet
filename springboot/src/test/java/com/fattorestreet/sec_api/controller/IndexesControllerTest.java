package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IndexesController.class)
class IndexesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketIndexRepository marketIndexRepository;

    @Test
    void listIndexes_returns200() throws Exception {
        MarketIndex mi = new MarketIndex();
        mi.setCode("FAT50");
        mi.setDisplayName("Fattore 50");
        when(marketIndexRepository.findAll()).thenReturn(List.of(mi));

        mockMvc.perform(get("/indexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("FAT50"))
                .andExpect(jsonPath("$[0].displayName").value("Fattore 50"));
    }
}


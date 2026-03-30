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
        MarketIndex fat50 = new MarketIndex();
        fat50.setCode("FAT50");
        fat50.setDisplayName("Fattore 50");
        MarketIndex fat100 = new MarketIndex();
        fat100.setCode("FAT100");
        fat100.setDisplayName("Fattore 100");
        MarketIndex fat1000 = new MarketIndex();
        fat1000.setCode("FAT1000");
        fat1000.setDisplayName("Fattore 1000");
        when(marketIndexRepository.findAll()).thenReturn(List.of(fat50, fat100, fat1000));

        mockMvc.perform(get("/indexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("FAT100"))
                .andExpect(jsonPath("$[0].displayName").value("Fattore 100"))
                .andExpect(jsonPath("$[1].code").value("FAT1000"))
                .andExpect(jsonPath("$[1].displayName").value("Fattore 1000"))
                .andExpect(jsonPath("$[2].code").value("FAT50"))
                .andExpect(jsonPath("$[2].displayName").value("Fattore 50"));
    }
}


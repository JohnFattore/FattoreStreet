package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
public class IndexesController {

    private final MarketIndexRepository marketIndexRepository;

    public IndexesController(MarketIndexRepository marketIndexRepository) {
        this.marketIndexRepository = marketIndexRepository;
    }

    public record MarketIndexRow(String code, String displayName) {
    }

    @GetMapping("/indexes")
    public List<MarketIndexRow> listIndexes() {
        return marketIndexRepository.findAll().stream()
                .sorted(Comparator.comparing(mi -> mi.getCode() != null ? mi.getCode() : ""))
                .map(this::toRow)
                .toList();
    }

    private MarketIndexRow toRow(MarketIndex mi) {
        return new MarketIndexRow(mi.getCode(), mi.getDisplayName());
    }
}


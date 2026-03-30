package com.fattorestreet.sec_api.config;

import com.fattorestreet.sec_api.index.FattoreIndexCodes;
import com.fattorestreet.sec_api.index.FattoreIndexRebuildService;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FattoreIndexRebuildConfig {

    @Bean("fattore50IndexRebuildService")
    public FattoreIndexRebuildService fattore50IndexRebuildService(
            ListingIndexMetricsRepository metricsRepository,
            MarketIndexRepository marketIndexRepository,
            IndexMemberRepository indexMemberRepository,
            @Value("${fattore50.rebuild.top-n:50}") int topN) {
        return new FattoreIndexRebuildService(
                metricsRepository,
                marketIndexRepository,
                indexMemberRepository,
                FattoreIndexCodes.FAT50,
                "Fattore 50",
                topN);
    }

    @Bean("fattore100IndexRebuildService")
    public FattoreIndexRebuildService fattore100IndexRebuildService(
            ListingIndexMetricsRepository metricsRepository,
            MarketIndexRepository marketIndexRepository,
            IndexMemberRepository indexMemberRepository,
            @Value("${fattore100.rebuild.top-n:100}") int topN) {
        return new FattoreIndexRebuildService(
                metricsRepository,
                marketIndexRepository,
                indexMemberRepository,
                FattoreIndexCodes.FAT100,
                "Fattore 100",
                topN);
    }

    @Bean("fattore1000IndexRebuildService")
    public FattoreIndexRebuildService fattore1000IndexRebuildService(
            ListingIndexMetricsRepository metricsRepository,
            MarketIndexRepository marketIndexRepository,
            IndexMemberRepository indexMemberRepository,
            @Value("${fattore1000.rebuild.top-n:1000}") int topN) {
        return new FattoreIndexRebuildService(
                metricsRepository,
                marketIndexRepository,
                indexMemberRepository,
                FattoreIndexCodes.FAT1000,
                "Fattore 1000",
                topN);
    }
}

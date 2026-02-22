package com.example.sec_api.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.sec_api.model.Asset;
import com.example.sec_api.model.FilingSummary;

public interface FilingSummaryRepository extends JpaRepository<FilingSummary, Long> {

    List<FilingSummary> findByTickerOrderByFilingDateDesc(String ticker);

    boolean existsByAccessionNumber(String accessionNumber);

    List<FilingSummary> findByAssetOrderByFilingDateDesc(Asset asset);
}

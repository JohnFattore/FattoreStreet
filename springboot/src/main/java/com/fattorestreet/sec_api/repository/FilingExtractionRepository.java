package com.fattorestreet.sec_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fattorestreet.sec_api.model.FilingExtraction;

public interface FilingExtractionRepository extends JpaRepository<FilingExtraction, Long> {

    List<FilingExtraction> findByCik(Long cik);
}

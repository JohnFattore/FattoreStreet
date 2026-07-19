package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-accession cache of corporate-action text extraction results. Filings are immutable, so
 * once a document has been fetched and scanned its extracted record-date / ex-date / declaration
 * / split-date candidates are persisted here and re-scans skip the HTTP fetch entirely. The
 * per-section extractor versions invalidate stored results when the extraction logic changes.
 */
@Entity
@Table(name = "filing_extractions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cik", "accession_number"}))
public class FilingExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cik;

    @Column(name = "accession_number", nullable = false, length = 40)
    private String accessionNumber;

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "form_type", length = 20)
    private String formType;

    /** Version of the dividend extraction logic that produced the dividend section; null = not scanned. */
    @Column(name = "dividend_extractor_version")
    private Integer dividendExtractorVersion;

    /** Version of the split extraction logic that produced the split section; null = not scanned. */
    @Column(name = "split_extractor_version")
    private Integer splitExtractorVersion;

    @Column(name = "best_record_date")
    private LocalDate bestRecordDate;

    @Column(name = "best_record_date_score")
    private Integer bestRecordDateScore;

    /** JSON array of {date, score} ex-dividend date candidates from this filing. */
    @Column(name = "ex_date_candidates_json", columnDefinition = "TEXT")
    private String exDateCandidatesJson;

    /** JSON array of declaration tuples (amount, record/payable/declaration/ex dates, confidence). */
    @Column(name = "declarations_json", columnDefinition = "TEXT")
    private String declarationsJson;

    @Column(name = "best_split_date")
    private LocalDate bestSplitDate;

    @Column(name = "best_split_date_score")
    private Integer bestSplitDateScore;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCik() {
        return cik;
    }

    public void setCik(Long cik) {
        this.cik = cik;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(LocalDate filingDate) {
        this.filingDate = filingDate;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public Integer getDividendExtractorVersion() {
        return dividendExtractorVersion;
    }

    public void setDividendExtractorVersion(Integer dividendExtractorVersion) {
        this.dividendExtractorVersion = dividendExtractorVersion;
    }

    public Integer getSplitExtractorVersion() {
        return splitExtractorVersion;
    }

    public void setSplitExtractorVersion(Integer splitExtractorVersion) {
        this.splitExtractorVersion = splitExtractorVersion;
    }

    public LocalDate getBestRecordDate() {
        return bestRecordDate;
    }

    public void setBestRecordDate(LocalDate bestRecordDate) {
        this.bestRecordDate = bestRecordDate;
    }

    public Integer getBestRecordDateScore() {
        return bestRecordDateScore;
    }

    public void setBestRecordDateScore(Integer bestRecordDateScore) {
        this.bestRecordDateScore = bestRecordDateScore;
    }

    public String getExDateCandidatesJson() {
        return exDateCandidatesJson;
    }

    public void setExDateCandidatesJson(String exDateCandidatesJson) {
        this.exDateCandidatesJson = exDateCandidatesJson;
    }

    public String getDeclarationsJson() {
        return declarationsJson;
    }

    public void setDeclarationsJson(String declarationsJson) {
        this.declarationsJson = declarationsJson;
    }

    public LocalDate getBestSplitDate() {
        return bestSplitDate;
    }

    public void setBestSplitDate(LocalDate bestSplitDate) {
        this.bestSplitDate = bestSplitDate;
    }

    public Integer getBestSplitDateScore() {
        return bestSplitDateScore;
    }

    public void setBestSplitDateScore(Integer bestSplitDateScore) {
        this.bestSplitDateScore = bestSplitDateScore;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}

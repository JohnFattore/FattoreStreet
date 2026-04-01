package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "corporate_actions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "action_type", "effective_date", "ratio"}))
public class CorporateAction {

    public enum ActionType { SPLIT, DIVIDEND }
    public enum SourceType { SEC_EQUITY_XBRL, SEC_ETF_FILING, MANUAL, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 10)
    private ActionType actionType;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** For SPLIT: old_shares / new_shares (e.g. 0.25 for a 4:1 forward split).
     *  For DIVIDEND: the per-share cash amount. */
    @Column(nullable = false)
    private Double ratio;

    /** SEC-derived quarterly per-share cash amount before split-forward adjustment. */
    @Column(name = "raw_dividend")
    private Double rawDividend;

    /** Dividend amount adjusted to current-share basis using future split factors. */
    @Column(name = "adjusted_dividend")
    private Double adjustedDividend;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32)
    private SourceType sourceType;

    @Column(name = "accession_number", length = 40)
    private String accessionNumber;

    @Column(name = "form_type", length = 20)
    private String formType;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "sec_series_id", length = 32)
    private String secSeriesId;

    @Column(name = "sec_class_contract_id", length = 32)
    private String secClassContractId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }

    public Double getRawDividend() { return rawDividend; }
    public void setRawDividend(Double rawDividend) { this.rawDividend = rawDividend; }

    public Double getAdjustedDividend() { return adjustedDividend; }
    public void setAdjustedDividend(Double adjustedDividend) { this.adjustedDividend = adjustedDividend; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }

    public LocalDate getPayDate() { return payDate; }
    public void setPayDate(LocalDate payDate) { this.payDate = payDate; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getSecSeriesId() { return secSeriesId; }
    public void setSecSeriesId(String secSeriesId) { this.secSeriesId = secSeriesId; }

    public String getSecClassContractId() { return secClassContractId; }
    public void setSecClassContractId(String secClassContractId) { this.secClassContractId = secClassContractId; }
}

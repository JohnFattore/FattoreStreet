package com.example.sec_api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "corporate_actions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "action_type", "effective_date"}))
public class CorporateAction {

    public enum ActionType { SPLIT, DIVIDEND }

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
}

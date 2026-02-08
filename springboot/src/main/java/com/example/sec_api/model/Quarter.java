package com.example.sec_api.model;

import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "quarters")
public class Quarter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate periodEnd;

    private LocalDate periodStart;

    private Integer year;

    private Integer quarter;

    // Income Statement
    private Long revenues;
    private Long netIncomeLoss;
    private Long operatingIncomeLoss;
    private Long grossProfit;
    private Double earningsPerShareBasic;
    private Double earningsPerShareDiluted;

    // Balance Sheet
    private Long assets;
    private Long liabilities;
    private Long stockholdersEquity;
    private Long cashAndCashEquivalentsAtCarryingValue;
    private Long accountsReceivableNetCurrent;
    private Long inventoryNet;

    // Cash Flow
    private Long netCashProvidedByUsedInOperatingActivities;
    private Long paymentsOfDividends;
    private Long paymentsForRepurchaseOfCommonStock;

    @ManyToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;

    // --- getters and setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getQuarter() {
        return quarter;
    }

    public void setQuarter(Integer quarter) {
        this.quarter = quarter;
    }

    public Long getRevenues() {
        return revenues;
    }

    public void setRevenues(Long revenues) {
        this.revenues = revenues;
    }

    public Long getNetIncomeLoss() {
        return netIncomeLoss;
    }

    public void setNetIncomeLoss(Long netIncomeLoss) {
        this.netIncomeLoss = netIncomeLoss;
    }

    public Long getOperatingIncomeLoss() {
        return operatingIncomeLoss;
    }

    public void setOperatingIncomeLoss(Long operatingIncomeLoss) {
        this.operatingIncomeLoss = operatingIncomeLoss;
    }

    public Long getGrossProfit() {
        return grossProfit;
    }

    public void setGrossProfit(Long grossProfit) {
        this.grossProfit = grossProfit;
    }

    public Double getEarningsPerShareBasic() {
        return earningsPerShareBasic;
    }

    public void setEarningsPerShareBasic(Double earningsPerShareBasic) {
        this.earningsPerShareBasic = earningsPerShareBasic;
    }

    public Double getEarningsPerShareDiluted() {
        return earningsPerShareDiluted;
    }

    public void setEarningsPerShareDiluted(Double earningsPerShareDiluted) {
        this.earningsPerShareDiluted = earningsPerShareDiluted;
    }

    public Long getAssets() {
        return assets;
    }

    public void setAssets(Long assets) {
        this.assets = assets;
    }

    public Long getLiabilities() {
        return liabilities;
    }

    public void setLiabilities(Long liabilities) {
        this.liabilities = liabilities;
    }

    public Long getStockholdersEquity() {
        return stockholdersEquity;
    }

    public void setStockholdersEquity(Long stockholdersEquity) {
        this.stockholdersEquity = stockholdersEquity;
    }

    public Long getCashAndCashEquivalentsAtCarryingValue() {
        return cashAndCashEquivalentsAtCarryingValue;
    }

    public void setCashAndCashEquivalentsAtCarryingValue(Long cashAndCashEquivalentsAtCarryingValue) {
        this.cashAndCashEquivalentsAtCarryingValue = cashAndCashEquivalentsAtCarryingValue;
    }

    public Long getAccountsReceivableNetCurrent() {
        return accountsReceivableNetCurrent;
    }

    public void setAccountsReceivableNetCurrent(Long accountsReceivableNetCurrent) {
        this.accountsReceivableNetCurrent = accountsReceivableNetCurrent;
    }

    public Long getInventoryNet() {
        return inventoryNet;
    }

    public void setInventoryNet(Long inventoryNet) {
        this.inventoryNet = inventoryNet;
    }

    public Long getNetCashProvidedByUsedInOperatingActivities() {
        return netCashProvidedByUsedInOperatingActivities;
    }

    public void setNetCashProvidedByUsedInOperatingActivities(Long netCashProvidedByUsedInOperatingActivities) {
        this.netCashProvidedByUsedInOperatingActivities = netCashProvidedByUsedInOperatingActivities;
    }

    public Long getPaymentsOfDividends() {
        return paymentsOfDividends;
    }

    public void setPaymentsOfDividends(Long paymentsOfDividends) {
        this.paymentsOfDividends = paymentsOfDividends;
    }

    public Long getPaymentsForRepurchaseOfCommonStock() {
        return paymentsForRepurchaseOfCommonStock;
    }

    public void setPaymentsForRepurchaseOfCommonStock(Long paymentsForRepurchaseOfCommonStock) {
        this.paymentsForRepurchaseOfCommonStock = paymentsForRepurchaseOfCommonStock;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }
}
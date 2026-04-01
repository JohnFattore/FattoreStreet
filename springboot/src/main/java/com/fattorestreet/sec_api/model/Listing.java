package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;

import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "sec_series_id", length = 32)
    private String secSeriesId;

    @Column(name = "sec_class_contract_id", length = 32)
    private String secClassContractId;

    @Column(name = "sec_class_ticker", length = 16)
    private String secClassTicker;

    @Column(name = "sec_series_name", length = 255)
    private String secSeriesName;

    @Column(name = "sec_class_name", length = 255)
    private String secClassName;

    @Column(name = "sec_identity_status", length = 24)
    private String secIdentityStatus;
    
    @ManyToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;    

    // --- getters and setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSecSeriesId() { return secSeriesId; }
    public void setSecSeriesId(String secSeriesId) { this.secSeriesId = secSeriesId; }

    public String getSecClassContractId() { return secClassContractId; }
    public void setSecClassContractId(String secClassContractId) { this.secClassContractId = secClassContractId; }

    public String getSecClassTicker() { return secClassTicker; }
    public void setSecClassTicker(String secClassTicker) { this.secClassTicker = secClassTicker; }

    public String getSecSeriesName() { return secSeriesName; }
    public void setSecSeriesName(String secSeriesName) { this.secSeriesName = secSeriesName; }

    public String getSecClassName() { return secClassName; }
    public void setSecClassName(String secClassName) { this.secClassName = secClassName; }

    public String getSecIdentityStatus() { return secIdentityStatus; }
    public void setSecIdentityStatus(String secIdentityStatus) { this.secIdentityStatus = secIdentityStatus; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
}

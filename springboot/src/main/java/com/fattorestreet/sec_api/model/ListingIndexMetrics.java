package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Yahoo-free market metrics for index construction: populated from IEX-derived {@link DailyPrice}
 * and SEC company facts (shares / float / DEI).
 */
@Entity
@Table(name = "listing_index_metrics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "year"}))
public class ListingIndexMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "market_cap", precision = 38, scale = 5)
    private BigDecimal marketCap;

    @Column(precision = 38, scale = 5)
    private BigDecimal volume;

    @Column(name = "volume_usd", precision = 38, scale = 5)
    private BigDecimal volumeUsd;

    @Column(name = "free_float", precision = 38, scale = 5)
    private BigDecimal freeFloat;

    @Column(name = "free_float_market_cap", precision = 38, scale = 5)
    private BigDecimal freeFloatMarketCap;

    @Column(name = "country_incorp", length = 1000)
    private String countryIncorp = "United States";

    @Column(name = "country_hq", length = 1000)
    private String countryHq = "United States";

    @Column(name = "state_incorp", length = 1000)
    private String stateIncorp;

    @Column(name = "state_hq", length = 1000)
    private String stateHq;

    @Column(name = "security_type", length = 1000)
    private String securityType = "Common Stock";

    @Column(name = "year_ipo")
    private Integer yearIpo = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Listing getListing() {
        return listing;
    }

    public void setListing(Listing listing) {
        this.listing = listing;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getVolumeUsd() {
        return volumeUsd;
    }

    public void setVolumeUsd(BigDecimal volumeUsd) {
        this.volumeUsd = volumeUsd;
    }

    public BigDecimal getFreeFloat() {
        return freeFloat;
    }

    public void setFreeFloat(BigDecimal freeFloat) {
        this.freeFloat = freeFloat;
    }

    public BigDecimal getFreeFloatMarketCap() {
        return freeFloatMarketCap;
    }

    public void setFreeFloatMarketCap(BigDecimal freeFloatMarketCap) {
        this.freeFloatMarketCap = freeFloatMarketCap;
    }

    public String getCountryIncorp() {
        return countryIncorp;
    }

    public void setCountryIncorp(String countryIncorp) {
        this.countryIncorp = countryIncorp;
    }

    public String getCountryHq() {
        return countryHq;
    }

    public void setCountryHq(String countryHq) {
        this.countryHq = countryHq;
    }

    public String getStateIncorp() {
        return stateIncorp;
    }

    public void setStateIncorp(String stateIncorp) {
        this.stateIncorp = stateIncorp;
    }

    public String getStateHq() {
        return stateHq;
    }

    public void setStateHq(String stateHq) {
        this.stateHq = stateHq;
    }

    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public Integer getYearIpo() {
        return yearIpo;
    }

    public void setYearIpo(Integer yearIpo) {
        this.yearIpo = yearIpo;
    }
}

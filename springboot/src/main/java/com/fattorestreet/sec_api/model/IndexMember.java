package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "index_members")
public class IndexMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false, precision = 38, scale = 5)
    private BigDecimal percent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_index_id", nullable = false)
    private MarketIndex marketIndex;

    @Column(nullable = false)
    private boolean outlier = false;

    @Column(length = 100_000)
    private String notes = "";

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

    public BigDecimal getPercent() {
        return percent;
    }

    public void setPercent(BigDecimal percent) {
        this.percent = percent;
    }

    public MarketIndex getMarketIndex() {
        return marketIndex;
    }

    public void setMarketIndex(MarketIndex marketIndex) {
        this.marketIndex = marketIndex;
    }

    public boolean isOutlier() {
        return outlier;
    }

    public void setOutlier(boolean outlier) {
        this.outlier = outlier;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

package com.example.sec_api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String title;
    
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

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
}

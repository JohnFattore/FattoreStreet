package com.example.sec_api.model;

import jakarta.persistence.*;
import java.util.*;
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long cik;

    private Boolean isFund;

    @OneToMany(mappedBy = "asset")
    private List<Listing> listings;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCik() { return cik; }
    public void setCik(Long cik) { this.cik = cik; }

    public Boolean getIsFund() { return isFund; }
    public void setIsFund(Boolean isFund) { this.isFund = isFund; }

    public List<Listing> getListings() { return listings; }
    public void setListings(List<Listing> listings) { this.listings = listings; }
}

package com.fattorestreet.sec_api.model;

import jakarta.persistence.*;

import org.hibernate.envers.Audited;

/**
 * One row per tradable index definition. {@code code} is the stable identifier used in repositories
 * and admin flows; {@code displayName} is the human label exposed in API JSON as {@code index}.
 */
@Entity
@Audited
@Table(name = "market_indexes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "code"),
                @UniqueConstraint(columnNames = "display_name")
        })
public class MarketIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 500)
    private String displayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

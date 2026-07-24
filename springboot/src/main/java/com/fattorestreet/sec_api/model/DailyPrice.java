package com.fattorestreet.sec_api.model;

import java.time.LocalDate;

import jakarta.persistence.*;

import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "daily_prices",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "trade_date"}))
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "daily_price_seq")
    @SequenceGenerator(name = "daily_price_seq", sequenceName = "daily_prices_id_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price")
    private Double openPrice;

    @Column(name = "high_price")
    private Double highPrice;

    @Column(name = "low_price")
    private Double lowPrice;

    @Column(name = "close_price")
    private Double closePrice;

    private Long volume;

    @Column(name = "adjusted_open")
    private Double adjustedOpen;

    @Column(name = "adjusted_high")
    private Double adjustedHigh;

    @Column(name = "adjusted_low")
    private Double adjustedLow;

    @Column(name = "adjusted_close")
    private Double adjustedClose;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public Double getOpenPrice() { return openPrice; }
    public void setOpenPrice(Double openPrice) { this.openPrice = openPrice; }

    public Double getHighPrice() { return highPrice; }
    public void setHighPrice(Double highPrice) { this.highPrice = highPrice; }

    public Double getLowPrice() { return lowPrice; }
    public void setLowPrice(Double lowPrice) { this.lowPrice = lowPrice; }

    public Double getClosePrice() { return closePrice; }
    public void setClosePrice(Double closePrice) { this.closePrice = closePrice; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }

    public Double getAdjustedOpen() { return adjustedOpen; }
    public void setAdjustedOpen(Double adjustedOpen) { this.adjustedOpen = adjustedOpen; }

    public Double getAdjustedHigh() { return adjustedHigh; }
    public void setAdjustedHigh(Double adjustedHigh) { this.adjustedHigh = adjustedHigh; }

    public Double getAdjustedLow() { return adjustedLow; }
    public void setAdjustedLow(Double adjustedLow) { this.adjustedLow = adjustedLow; }

    public Double getAdjustedClose() { return adjustedClose; }
    public void setAdjustedClose(Double adjustedClose) { this.adjustedClose = adjustedClose; }
}

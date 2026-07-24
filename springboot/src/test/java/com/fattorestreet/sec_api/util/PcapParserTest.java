package com.fattorestreet.sec_api.util;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fattorestreet.sec_api.testsupport.PcapTestData;
import com.fattorestreet.sec_api.util.PcapParser.TradeReport;

import static org.junit.jupiter.api.Assertions.*;

class PcapParserTest {

    private static List<TradeReport> parse(byte[] capture) throws IOException {
        List<TradeReport> trades = new ArrayList<>();
        PcapParser.parse(new ByteArrayInputStream(capture), trades::add);
        return trades;
    }

    @Test
    void parsesSingleTradeReport() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(PcapTestData.tradeReport(1_000L, "AAPL", 100, 172.53)));

        List<TradeReport> trades = parse(capture);

        assertEquals(1, trades.size());
        TradeReport tr = trades.get(0);
        assertEquals("AAPL", tr.symbol());
        assertEquals(1_000L, tr.timestamp());
        assertEquals(100, tr.size());
        assertEquals(172.53, tr.price());
    }

    @Test
    void returnsTradeCount() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(
                        PcapTestData.tradeReport(1L, "AAPL", 100, 10.0),
                        PcapTestData.tradeReport(2L, "MSFT", 200, 20.0)));

        long count = PcapParser.parse(new ByteArrayInputStream(capture), tr -> { });

        assertEquals(2, count);
    }

    @Test
    void parsesTradesAcrossMultiplePackets() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(PcapTestData.tradeReport(1L, "AAPL", 100, 10.0)),
                PcapTestData.udpPacket(PcapTestData.tradeReport(2L, "MSFT", 200, 20.0)),
                PcapTestData.udpPacket(PcapTestData.tradeReport(3L, "AAPL", 300, 10.5)));

        List<TradeReport> trades = parse(capture);

        assertEquals(3, trades.size());
        assertEquals(List.of("AAPL", "MSFT", "AAPL"),
                trades.stream().map(TradeReport::symbol).toList());
    }

    @Test
    void skipsNonTradeMessages() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(
                        PcapTestData.quoteUpdate(),
                        PcapTestData.tradeReport(1L, "NVDA", 50, 900.25),
                        PcapTestData.quoteUpdate()));

        List<TradeReport> trades = parse(capture);

        assertEquals(1, trades.size());
        assertEquals("NVDA", trades.get(0).symbol());
    }

    @Test
    void skipsNonIpv4Packets() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.nonIpv4Packet(PcapTestData.tradeReport(1L, "AAPL", 100, 10.0)),
                PcapTestData.udpPacket(PcapTestData.tradeReport(2L, "MSFT", 200, 20.0)));

        List<TradeReport> trades = parse(capture);

        assertEquals(1, trades.size());
        assertEquals("MSFT", trades.get(0).symbol());
    }

    @Test
    void dropsTradesWithNonPositivePriceOrSize() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(
                        PcapTestData.tradeReport(1L, "AAPL", 0, 10.0),
                        PcapTestData.tradeReport(2L, "MSFT", 100, 0.0),
                        PcapTestData.tradeReport(3L, "NVDA", 100, 10.0)));

        List<TradeReport> trades = parse(capture);

        assertEquals(1, trades.size());
        assertEquals("NVDA", trades.get(0).symbol());
    }

    @Test
    void emptyCaptureYieldsNoTrades() throws IOException {
        List<TradeReport> trades = parse(PcapTestData.pcap());
        assertTrue(trades.isEmpty());
    }

    @Test
    void unknownMagicNumberThrows() {
        byte[] garbage = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        IOException e = assertThrows(IOException.class, () -> parse(garbage));
        assertTrue(e.getMessage().contains("Unknown capture format"));
    }

    @Test
    void truncatedPacketPayloadThrowsEof() {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(PcapTestData.tradeReport(1L, "AAPL", 100, 10.0)));
        byte[] truncated = Arrays.copyOf(capture, capture.length - 10);

        assertThrows(EOFException.class, () -> parse(truncated));
    }

    @Test
    void parseGzip_readsGzippedCapture(@TempDir Path tempDir) throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(
                        PcapTestData.tradeReport(1L, "AAPL", 100, 172.53),
                        PcapTestData.tradeReport(2L, "AAPL", 50, 172.60)));
        Path gz = tempDir.resolve("tops.pcap.gz");
        PcapTestData.writeGzip(gz, capture);

        List<TradeReport> trades = new ArrayList<>();
        long count = PcapParser.parseGzip(gz.toFile(), trades::add);

        assertEquals(2, count);
        assertEquals(2, trades.size());
        assertEquals(172.60, trades.get(1).price());
    }

    @Test
    void symbolsArePaddedAsciiAndTrimmed() throws IOException {
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(PcapTestData.tradeReport(1L, "F", 10, 12.0)));

        List<TradeReport> trades = parse(capture);

        assertEquals("F", trades.get(0).symbol());
    }
}

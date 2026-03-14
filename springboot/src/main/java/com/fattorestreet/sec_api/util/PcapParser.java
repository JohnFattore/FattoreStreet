package com.fattorestreet.sec_api.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Streaming parser for IEX HIST TOPS pcap/pcapng files.
 * Extracts Trade Report messages (type 0x54) and passes them to a consumer.
 *
 * Optimized for throughput: reuses byte buffers, interns symbol strings,
 * and uses large I/O buffers to minimize syscalls and GC pressure.
 */
public class PcapParser {

    private static final int PCAP_MAGIC_LE = 0xA1B2C3D4;
    private static final int PCAPNG_SHB_MAGIC = 0x0A0D0D0A;
    private static final int PCAPNG_EPB_TYPE = 0x00000006;

    private static final int ETHERNET_HEADER_SIZE = 14;
    private static final int UDP_HEADER_SIZE = 8;
    private static final int IEX_TP_HEADER_SIZE = 40;
    private static final int ETH_TYPE_IPV4 = 0x0800;
    private static final byte IP_PROTO_UDP = 17;

    private static final byte TRADE_REPORT_TYPE = 0x54;
    private static final int TRADE_REPORT_MIN_LEN = 38;

    public record TradeReport(long timestamp, String symbol, int size, double price) {}

    public static long parse(InputStream rawInput, Consumer<TradeReport> consumer) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(rawInput, 1 << 20);
        DataInputStream in = new DataInputStream(buffered);

        byte[] magicBytes = new byte[4];
        in.readFully(magicBytes);
        int magic = ByteBuffer.wrap(magicBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        Map<Long, String> symbolCache = new HashMap<>(16384);

        if (magic == PCAP_MAGIC_LE || magic == Integer.reverseBytes(PCAP_MAGIC_LE)) {
            in.skipBytes(20);
            return parsePcapPackets(in, consumer, symbolCache);
        } else if (magic == PCAPNG_SHB_MAGIC) {
            return parsePcapngBlocks(in, consumer, symbolCache);
        } else {
            throw new IOException("Unknown capture format (magic: 0x" + Integer.toHexString(magic) + ")");
        }
    }

    public static long parseGzip(File gzFile, Consumer<TradeReport> consumer) throws IOException {
        try (InputStream fis = new FileInputStream(gzFile);
             BufferedInputStream bis = new BufferedInputStream(fis, 1 << 18);
             GZIPInputStream gzis = new GZIPInputStream(bis, 1 << 18)) {
            return parse(gzis, consumer);
        }
    }

    private static long parsePcapPackets(DataInputStream in, Consumer<TradeReport> consumer,
                                         Map<Long, String> symbolCache) throws IOException {
        long tradeCount = 0;
        byte[] hdr = new byte[16];
        byte[] payload = new byte[65536];

        while (true) {
            try {
                in.readFully(hdr);
            } catch (EOFException e) {
                break;
            }

            int capturedLen = ByteBuffer.wrap(hdr, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (capturedLen > payload.length) {
                payload = new byte[capturedLen];
            }
            in.readFully(payload, 0, capturedLen);

            tradeCount += extractTrades(payload, capturedLen, consumer, symbolCache);
        }
        return tradeCount;
    }

    private static long parsePcapngBlocks(DataInputStream in, Consumer<TradeReport> consumer,
                                          Map<Long, String> symbolCache) throws IOException {
        long tradeCount = 0;

        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int shbLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int remaining = shbLen - 8;
        if (remaining > 0) in.skipBytes(remaining);

        byte[] blockHeader = new byte[8];
        byte[] blockData = new byte[65536];

        while (true) {
            try {
                in.readFully(blockHeader);
            } catch (EOFException e) {
                break;
            }

            ByteBuffer bh = ByteBuffer.wrap(blockHeader).order(ByteOrder.LITTLE_ENDIAN);
            int blockType = bh.getInt();
            int blockTotalLength = bh.getInt();

            remaining = blockTotalLength - 8;
            if (remaining < 4) break;

            if (blockType == PCAPNG_EPB_TYPE) {
                if (remaining > blockData.length) {
                    blockData = new byte[remaining];
                }
                in.readFully(blockData, 0, remaining);

                int bodyLen = remaining - 4;
                if (bodyLen >= 20) {
                    int capturedLen = ByteBuffer.wrap(blockData, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (20 + capturedLen <= bodyLen) {
                        tradeCount += extractTradesFromBlock(blockData, 20, capturedLen, consumer, symbolCache);
                    }
                }
            } else {
                in.skipBytes(remaining);
            }
        }
        return tradeCount;
    }

    private static int extractTrades(byte[] payload, int plen, Consumer<TradeReport> consumer,
                                     Map<Long, String> symbolCache) {
        int minSize = ETHERNET_HEADER_SIZE + 20 + UDP_HEADER_SIZE + IEX_TP_HEADER_SIZE;
        if (plen < minSize) return 0;

        int ethType = ((payload[12] & 0xFF) << 8) | (payload[13] & 0xFF);
        if (ethType != ETH_TYPE_IPV4) return 0;

        int ipv4Start = ETHERNET_HEADER_SIZE;
        int ipv4Ihl = (payload[ipv4Start] & 0x0F) * 4;
        if (ipv4Ihl < 20) return 0;
        if (payload[ipv4Start + 9] != IP_PROTO_UDP) return 0;

        int iexStart = ipv4Start + ipv4Ihl + UDP_HEADER_SIZE;
        if (iexStart + IEX_TP_HEADER_SIZE > plen) return 0;

        int msgCount = ((payload[iexStart + 14] & 0xFF) | ((payload[iexStart + 15] & 0xFF) << 8));
        if (msgCount == 0 || msgCount > 10000) return 0;

        int count = 0;
        int offset = iexStart + IEX_TP_HEADER_SIZE;

        for (int i = 0; i < msgCount; i++) {
            if (offset + 2 > plen) break;
            int msgLen = ((payload[offset] & 0xFF) | ((payload[offset + 1] & 0xFF) << 8));
            offset += 2;
            if (msgLen == 0 || offset + msgLen > plen) break;

            if (msgLen >= TRADE_REPORT_MIN_LEN && payload[offset] == TRADE_REPORT_TYPE) {
                try {
                    TradeReport tr = parseTradeReport(payload, offset + 1, symbolCache);
                    if (tr != null) {
                        consumer.accept(tr);
                        count++;
                    }
                } catch (Exception ignored) {
                }
            }
            offset += msgLen;
        }
        return count;
    }

    /** Extract trades from a pcapng EPB block without copying the payload to a separate array. */
    private static int extractTradesFromBlock(byte[] blockData, int payloadOffset, int capturedLen,
                                              Consumer<TradeReport> consumer, Map<Long, String> symbolCache) {
        int plen = capturedLen;
        int minSize = ETHERNET_HEADER_SIZE + 20 + UDP_HEADER_SIZE + IEX_TP_HEADER_SIZE;
        if (plen < minSize) return 0;

        int base = payloadOffset;
        int ethType = ((blockData[base + 12] & 0xFF) << 8) | (blockData[base + 13] & 0xFF);
        if (ethType != ETH_TYPE_IPV4) return 0;

        int ipv4Start = base + ETHERNET_HEADER_SIZE;
        int ipv4Ihl = (blockData[ipv4Start] & 0x0F) * 4;
        if (ipv4Ihl < 20) return 0;
        if (blockData[ipv4Start + 9] != IP_PROTO_UDP) return 0;

        int iexStart = ipv4Start + ipv4Ihl + UDP_HEADER_SIZE;
        if (iexStart + IEX_TP_HEADER_SIZE > base + plen) return 0;

        int msgCount = ((blockData[iexStart + 14] & 0xFF) | ((blockData[iexStart + 15] & 0xFF) << 8));
        if (msgCount == 0 || msgCount > 10000) return 0;

        int count = 0;
        int offset = iexStart + IEX_TP_HEADER_SIZE;
        int limit = base + plen;

        for (int i = 0; i < msgCount; i++) {
            if (offset + 2 > limit) break;
            int msgLen = ((blockData[offset] & 0xFF) | ((blockData[offset + 1] & 0xFF) << 8));
            offset += 2;
            if (msgLen == 0 || offset + msgLen > limit) break;

            if (msgLen >= TRADE_REPORT_MIN_LEN && blockData[offset] == TRADE_REPORT_TYPE) {
                try {
                    TradeReport tr = parseTradeReport(blockData, offset + 1, symbolCache);
                    if (tr != null) {
                        consumer.accept(tr);
                        count++;
                    }
                } catch (Exception ignored) {
                }
            }
            offset += msgLen;
        }
        return count;
    }

    private static TradeReport parseTradeReport(byte[] data, int off, Map<Long, String> symbolCache) {
        ByteBuffer buf = ByteBuffer.wrap(data, off, 37).order(ByteOrder.LITTLE_ENDIAN);
        buf.get(); // flags
        long timestamp = buf.getLong();

        long symKey = buf.getLong();
        String symbol = symbolCache.get(symKey);
        if (symbol == null) {
            byte[] symBytes = new byte[8];
            ByteBuffer.wrap(data, off + 9, 8).get(symBytes);
            symbol = new String(symBytes, StandardCharsets.US_ASCII).trim();
            if (symbol.isEmpty()) return null;
            symbolCache.put(symKey, symbol);
        }

        int size = buf.getInt();
        long priceRaw = buf.getLong();
        double price = priceRaw / 10_000.0;

        if (price <= 0 || size <= 0) return null;
        return new TradeReport(timestamp, symbol, size, price);
    }
}

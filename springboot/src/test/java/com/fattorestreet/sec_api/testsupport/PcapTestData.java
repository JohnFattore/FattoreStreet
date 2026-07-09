package com.fattorestreet.sec_api.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Builds classic-pcap byte streams carrying IEX TOPS messages for PcapParser tests,
 * so no binary fixtures need to be committed.
 */
public final class PcapTestData {

    private static final int PCAP_MAGIC_LE = 0xA1B2C3D4;
    public static final byte TRADE_REPORT_TYPE = 0x54;
    public static final byte QUOTE_UPDATE_TYPE = 0x51;

    private PcapTestData() {
    }

    /** A full capture: global header plus one UDP packet per call containing the given messages. */
    public static byte[] pcap(byte[]... packets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer global = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        global.putInt(PCAP_MAGIC_LE);
        global.putShort((short) 2).putShort((short) 4); // version 2.4
        global.putInt(0).putInt(0);                     // thiszone, sigfigs
        global.putInt(65535);                           // snaplen
        global.putInt(1);                               // linktype: Ethernet
        out.writeBytes(global.array());
        for (byte[] packet : packets) {
            ByteBuffer record = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            record.putInt(0).putInt(0);        // ts_sec, ts_usec
            record.putInt(packet.length);      // incl_len
            record.putInt(packet.length);      // orig_len
            out.writeBytes(record.array());
            out.writeBytes(packet);
        }
        return out.toByteArray();
    }

    /** Ethernet/IPv4/UDP packet wrapping an IEX transport segment with the given messages. */
    public static byte[] udpPacket(byte[]... messages) {
        return framedPacket(0x0800, (byte) 17, messages);
    }

    /** Same framing but a non-IPv4 ethertype; the parser must skip the whole packet. */
    public static byte[] nonIpv4Packet(byte[]... messages) {
        return framedPacket(0x86DD, (byte) 17, messages);
    }

    private static byte[] framedPacket(int ethType, byte ipProto, byte[]... messages) {
        int messagesLen = 0;
        for (byte[] m : messages) {
            messagesLen += 2 + m.length;
        }
        int iexLen = 40 + messagesLen;
        ByteBuffer buf = ByteBuffer.allocate(14 + 20 + 8 + iexLen).order(ByteOrder.LITTLE_ENDIAN);
        // Ethernet: dst + src MACs, then big-endian ethertype
        buf.put(new byte[12]);
        buf.put((byte) (ethType >> 8)).put((byte) ethType);
        // IPv4: version 4 / IHL 5, protocol at byte 9
        int ipv4Start = buf.position();
        buf.put(new byte[20]);
        buf.put(ipv4Start, (byte) 0x45);
        buf.put(ipv4Start + 9, ipProto);
        // UDP header (ports/length/checksum unused by the parser)
        buf.put(new byte[8]);
        // IEX transport header: message count is a little-endian short at offset 14
        int iexStart = buf.position();
        buf.put(new byte[40]);
        buf.putShort(iexStart + 14, (short) messages.length);
        for (byte[] m : messages) {
            buf.putShort((short) m.length);
            buf.put(m);
        }
        return buf.array();
    }

    /** IEX TOPS Trade Report message (type 0x54), 38 bytes. */
    public static byte[] tradeReport(long timestamp, String symbol, int size, double price) {
        ByteBuffer buf = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(TRADE_REPORT_TYPE);
        buf.put((byte) 0); // sale condition flags
        buf.putLong(timestamp);
        byte[] symbolBytes = String.format("%-8s", symbol).getBytes(StandardCharsets.US_ASCII);
        buf.put(symbolBytes, 0, 8);
        buf.putInt(size);
        buf.putLong(Math.round(price * 10_000.0));
        return buf.array();
    }

    /** A non-trade message of the same length; the parser must ignore it. */
    public static byte[] quoteUpdate() {
        ByteBuffer buf = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(QUOTE_UPDATE_TYPE);
        return buf.array();
    }

    public static void writeGzip(Path file, byte[] content) {
        try (OutputStream fos = Files.newOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(fos)) {
            gz.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

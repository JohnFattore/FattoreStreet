package com.fattorestreet.sec_api.marketdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.testsupport.PcapTestData;
import com.fattorestreet.sec_api.util.MarketTime;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IexHistServiceTest {

    private static final DateTimeFormatter KEY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private HttpClient httpClient;

    private IexHistService service;

    @BeforeEach
    void setUp() {
        service = new IexHistService(dailyPriceRepository, new ObjectMapper(), httpClient);
    }

    /** Most recent date before today that the service considers a trading day. */
    private static LocalDate latestTradingDay() {
        LocalDate date = LocalDate.now(MarketTime.MARKET).minusDays(1);
        while (!isTradingDay(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    // Mirrors IexHistService.isTradingDay so the test stays date-independent.
    private static boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        if (Set.of(MonthDay.of(1, 1), MonthDay.of(6, 19), MonthDay.of(7, 4), MonthDay.of(12, 25))
                .contains(MonthDay.from(date))) return false;
        int nth = (date.getDayOfMonth() - 1) / 7 + 1;
        if (date.getMonthValue() == 1 && dow == DayOfWeek.MONDAY && nth == 3) return false;
        if (date.getMonthValue() == 2 && dow == DayOfWeek.MONDAY && nth == 3) return false;
        if (date.getMonthValue() == 5 && dow == DayOfWeek.MONDAY && date.getDayOfMonth() > 24) return false;
        if (date.getMonthValue() == 9 && dow == DayOfWeek.MONDAY && nth == 1) return false;
        if (date.getMonthValue() == 11 && dow == DayOfWeek.THURSDAY && nth == 4) return false;
        return true;
    }

    private static String histIndexJson(LocalDate date) {
        return """
                {
                  "%s": [
                    {"feed": "DEEP", "link": "https://example.test/deep.pcap.gz", "size": "1048576"},
                    {"feed": "TOPS", "link": "https://example.test/tops.pcap.gz", "size": "2097152"}
                  ]
                }
                """.formatted(date.format(KEY_FMT));
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.toByteArray();
    }

    /**
     * Stubs the HttpClient: the HIST index URL answers with JSON, the TOPS download URL streams
     * the given bytes through the service's real ofFile BodyHandler so its temp file gets written.
     */
    private void stubHttp(String indexJson, byte[] downloadBytes) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.uri().toString().contains("iextrading.com/api/1.0/hist")) {
                        HttpResponse<String> response = mock(HttpResponse.class);
                        when(response.statusCode()).thenReturn(200);
                        when(response.body()).thenReturn(indexJson);
                        return response;
                    }
                    HttpResponse.BodyHandler<Object> handler = invocation.getArgument(1);
                    Object body = feedBodyHandler(handler, downloadBytes);
                    HttpResponse<Object> response = mock(HttpResponse.class);
                    lenient().when(response.statusCode()).thenReturn(200);
                    lenient().when(response.body()).thenReturn(body);
                    return response;
                });
    }

    private static <T> T feedBodyHandler(HttpResponse.BodyHandler<T> handler, byte[] bytes) {
        HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
            @Override public int statusCode() { return 200; }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        });
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { }
            @Override public void cancel() { }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
        subscriber.onComplete();
        return subscriber.getBody().toCompletableFuture().join();
    }

    @Test
    void loadHistData_aggregatesTradesIntoOhlcvAndSaves() throws Exception {
        LocalDate day = latestTradingDay();
        byte[] capture = PcapTestData.pcap(
                PcapTestData.udpPacket(
                        PcapTestData.tradeReport(100L, "AAPL", 10, 172.50),
                        PcapTestData.tradeReport(200L, "AAPL", 20, 174.00),
                        PcapTestData.tradeReport(300L, "AAPL", 30, 171.00),
                        PcapTestData.tradeReport(400L, "AAPL", 40, 173.25),
                        PcapTestData.tradeReport(250L, "MSFT", 5, 410.00)));
        stubHttp(histIndexJson(day), gzip(capture));
        when(dailyPriceRepository.existsByTradeDate(day)).thenReturn(false);

        Map<String, Object> result = service.loadHistData(1);

        assertEquals(1, result.get("processed"));
        assertEquals(0, result.get("errors"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).saveAll(captor.capture());
        List<DailyPrice> saved = captor.getValue();
        assertEquals(2, saved.size());

        DailyPrice aapl = saved.stream().filter(p -> p.getTicker().equals("AAPL")).findFirst().orElseThrow();
        assertEquals(day, aapl.getTradeDate());
        assertEquals(172.50, aapl.getOpenPrice());   // earliest timestamp
        assertEquals(174.00, aapl.getHighPrice());   // max price
        assertEquals(171.00, aapl.getLowPrice());    // min price
        assertEquals(173.25, aapl.getClosePrice());  // latest timestamp
        assertEquals(100L, aapl.getVolume());        // 10+20+30+40

        DailyPrice msft = saved.stream().filter(p -> p.getTicker().equals("MSFT")).findFirst().orElseThrow();
        assertEquals(410.00, msft.getOpenPrice());
        assertEquals(410.00, msft.getClosePrice());
        assertEquals(5L, msft.getVolume());
    }

    @Test
    void loadHistData_skipsDatesAlreadyInDatabase() throws Exception {
        LocalDate day = latestTradingDay();
        stubHttp(histIndexJson(day), new byte[0]);
        when(dailyPriceRepository.existsByTradeDate(day)).thenReturn(true);

        Map<String, Object> result = service.loadHistData(1);

        assertEquals(0, result.get("processed"));
        assertEquals(1, result.get("skipped"));
        verify(dailyPriceRepository, never()).saveAll(any());
        // Only the index fetch; no pcap download.
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void loadHistData_reportsDatesWithoutTopsFeed() throws Exception {
        LocalDate day = latestTradingDay();
        String indexWithoutTops = """
                {
                  "%s": [
                    {"feed": "DEEP", "link": "https://example.test/deep.pcap.gz", "size": "1048576"}
                  ]
                }
                """.formatted(day.format(KEY_FMT));
        stubHttp(indexWithoutTops, new byte[0]);
        when(dailyPriceRepository.existsByTradeDate(day)).thenReturn(false);

        Map<String, Object> result = service.loadHistData(1);

        assertEquals(0, result.get("processed"));
        assertEquals(1, result.get("notAvailable"));
    }

    @Test
    void loadHistData_nonTradingDaysAreNeverRequested() throws Exception {
        // Index only lists days the exchange published; an empty index means no dates qualify.
        stubHttp("{}", new byte[0]);

        Map<String, Object> result = service.loadHistData(3);

        assertEquals(0, result.get("processed"));
        assertEquals(0, result.get("skipped"));
        assertEquals(0, result.get("notAvailable"));
        verify(dailyPriceRepository, never()).existsByTradeDate(any());
    }

    @Test
    void loadHistData_indexHttpErrorThrows() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(invocation -> response);

        IOException e = assertThrows(IOException.class, () -> service.loadHistData(1));
        assertTrue(e.getMessage().contains("503"));
    }

    @Test
    void loadHistData_corruptDownloadCountsAsErrorAndContinues() throws Exception {
        LocalDate day = latestTradingDay();
        // Not gzip data: parseGzip fails, the day is recorded as an error, no save happens.
        stubHttp(histIndexJson(day), new byte[]{1, 2, 3, 4});
        when(dailyPriceRepository.existsByTradeDate(day)).thenReturn(false);

        Map<String, Object> result = service.loadHistData(1);

        assertEquals(0, result.get("processed"));
        assertEquals(1, result.get("errors"));
        verify(dailyPriceRepository, never()).saveAll(any());
    }
}

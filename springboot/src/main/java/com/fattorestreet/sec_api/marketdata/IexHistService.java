package com.fattorestreet.sec_api.marketdata;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.util.PcapParser;
import com.fattorestreet.sec_api.util.PcapParser.TradeReport;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class IexHistService {

    private static final Logger log = LoggerFactory.getLogger(IexHistService.class);
    private static final String HIST_INDEX_URL = "https://iextrading.com/api/1.0/hist";
    private static final DateTimeFormatter KEY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String USER_AGENT = "FattoreStreet/1.0";

    private static final Set<MonthDay> NYSE_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),   // New Year's Day
            MonthDay.of(6, 19),  // Juneteenth
            MonthDay.of(7, 4),   // Independence Day
            MonthDay.of(12, 25)  // Christmas Day
    );

    private final DailyPriceRepository dailyPriceRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "iex-download");
        t.setDaemon(true);
        return t;
    });

    public IexHistService(DailyPriceRepository dailyPriceRepository,
                          ObjectMapper objectMapper) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Run the full pipeline: fetch index, download TOPS PCAPs (with prefetch pipelining),
     * parse trades, aggregate to OHLCV, and batch-insert into the database.
     */
    public Map<String, Object> loadHistData(int days) throws Exception {
        log.info("Fetching IEX HIST index...");
        Map<String, List<Map<String, Object>>> histIndex = fetchHistIndex();
        log.info("Index contains {} trading days", histIndex.size());

        List<LocalDate> dates = tradingDates(days, histIndex.keySet());

        List<DayTask> toProcess = new ArrayList<>();
        int skipped = 0;
        int notAvailable = 0;

        for (LocalDate date : dates) {
            if (dailyPriceRepository.existsByTradeDate(date)) {
                skipped++;
                continue;
            }
            String dateKey = date.format(KEY_FMT);
            Map<String, Object> topsEntry = findTopsEntry(histIndex, dateKey);
            if (topsEntry == null) {
                notAvailable++;
                continue;
            }
            String link = (String) topsEntry.get("link");
            long sizeMb = Long.parseLong(topsEntry.get("size").toString()) / (1024 * 1024);
            toProcess.add(new DayTask(date, link, sizeMb));
        }

        int processed = 0;
        int errors = 0;

        CompletableFuture<Path> nextDownload = null;
        DayTask nextTask = null;

        if (!toProcess.isEmpty()) {
            nextTask = toProcess.get(0);
            log.info("[{}] Prefetching TOPS ({} MB)...", nextTask.date, nextTask.sizeMb);
            nextDownload = startAsyncDownload(nextTask.link);
        }

        for (int i = 0; i < toProcess.size(); i++) {
            DayTask current = toProcess.get(i);
            long dayStart = System.currentTimeMillis();

            Path tempFile = null;
            try {
                tempFile = nextDownload.join();
                long dlMs = System.currentTimeMillis() - dayStart;
                log.info("[{}] Download ready ({}s), parsing trades...", current.date, dlMs / 1000);

                if (i + 1 < toProcess.size()) {
                    DayTask ahead = toProcess.get(i + 1);
                    log.info("[{}] Prefetching TOPS ({} MB) in background...", ahead.date, ahead.sizeMb);
                    nextDownload = startAsyncDownload(ahead.link);
                }

                Map<String, OhlcvAccumulator> accumulators = new HashMap<>(16384);
                long tradeCount = PcapParser.parseGzip(tempFile.toFile(), tr ->
                    accumulators.computeIfAbsent(tr.symbol(), k -> new OhlcvAccumulator()).add(tr)
                );

                log.info("[{}] {} trades across {} symbols", current.date, tradeCount, accumulators.size());

                List<DailyPrice> batch = new ArrayList<>(accumulators.size());
                for (var entry : accumulators.entrySet()) {
                    OhlcvAccumulator acc = entry.getValue();
                    DailyPrice dp = new DailyPrice();
                    dp.setTicker(entry.getKey());
                    dp.setTradeDate(current.date);
                    dp.setOpenPrice(acc.openPrice);
                    dp.setHighPrice(acc.highPrice);
                    dp.setLowPrice(acc.lowPrice);
                    dp.setClosePrice(acc.closePrice);
                    dp.setVolume(acc.volume);
                    batch.add(dp);
                }

                dailyPriceRepository.saveAll(batch);

                long totalMs = System.currentTimeMillis() - dayStart;
                log.info("[{}] Saved {} symbols ({}m {}s total)", current.date, batch.size(),
                        totalMs / 60000, (totalMs % 60000) / 1000);
                processed++;

            } catch (Exception e) {
                log.error("[{}] Error: {}", current.date, e.getMessage());
                errors++;
                if (i + 1 < toProcess.size()) {
                    DayTask ahead = toProcess.get(i + 1);
                    nextDownload = startAsyncDownload(ahead.link);
                }
            } finally {
                if (tempFile != null) Files.deleteIfExists(tempFile);
            }
        }

        log.info("Done. Processed: {}, Skipped: {}, Not in index: {}, Errors: {}",
                processed, skipped, notAvailable, errors);

        return Map.of(
                "processed", processed,
                "skipped", skipped,
                "notAvailable", notAvailable,
                "errors", errors
        );
    }

    private CompletableFuture<Path> startAsyncDownload(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadToTempFile(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, downloadExecutor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> fetchHistIndex() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HIST_INDEX_URL))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HIST index returned HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                new TypeReference<Map<String, List<Map<String, Object>>>>() {});
    }

    private Map<String, Object> findTopsEntry(Map<String, List<Map<String, Object>>> index, String dateKey) {
        List<Map<String, Object>> entries = index.get(dateKey);
        if (entries == null) return null;
        for (Map<String, Object> entry : entries) {
            if ("TOPS".equals(entry.get("feed"))) return entry;
        }
        return null;
    }

    private Path downloadToTempFile(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();
        Path temp = Files.createTempFile("iex-tops-", ".pcap.gz");
        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        return temp;
    }

    private List<LocalDate> tradingDates(int numDays, Set<String> availableDates) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate date = LocalDate.now().minusDays(1);
        int lookback = numDays * 3;

        for (int i = 0; i < lookback && result.size() < numDays; i++) {
            if (isTradingDay(date) && availableDates.contains(date.format(KEY_FMT))) {
                result.add(date);
            }
            date = date.minusDays(1);
        }
        return result;
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        if (NYSE_HOLIDAYS.contains(MonthDay.from(date))) return false;
        if (date.getMonthValue() == 1 && dow == DayOfWeek.MONDAY && nthWeekday(date) == 3) return false;
        if (date.getMonthValue() == 2 && dow == DayOfWeek.MONDAY && nthWeekday(date) == 3) return false;
        if (date.getMonthValue() == 5 && dow == DayOfWeek.MONDAY && date.getDayOfMonth() > 24) return false;
        if (date.getMonthValue() == 9 && dow == DayOfWeek.MONDAY && nthWeekday(date) == 1) return false;
        if (date.getMonthValue() == 11 && dow == DayOfWeek.THURSDAY && nthWeekday(date) == 4) return false;
        return true;
    }

    private int nthWeekday(LocalDate date) {
        return (date.getDayOfMonth() - 1) / 7 + 1;
    }

    private record DayTask(LocalDate date, String link, long sizeMb) {}

    private static class OhlcvAccumulator {
        long openTimestamp = Long.MAX_VALUE;
        double openPrice;
        double highPrice = Double.MIN_VALUE;
        double lowPrice = Double.MAX_VALUE;
        long closeTimestamp = Long.MIN_VALUE;
        double closePrice;
        long volume;

        void add(TradeReport tr) {
            if (tr.timestamp() < openTimestamp) {
                openTimestamp = tr.timestamp();
                openPrice = tr.price();
            }
            if (tr.timestamp() > closeTimestamp) {
                closeTimestamp = tr.timestamp();
                closePrice = tr.price();
            }
            if (tr.price() > highPrice) highPrice = tr.price();
            if (tr.price() < lowPrice) lowPrice = tr.price();
            volume += tr.size();
        }
    }
}

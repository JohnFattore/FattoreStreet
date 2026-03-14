package com.fattorestreet.sec_api.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DailyPriceRepository dailyPriceRepository;

    public PriceService(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    public List<DailyPrice> getPrices(String ticker) {
        return dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
    }

    public List<DailyPrice> getPrices(String ticker, LocalDate start, LocalDate end) {
        return dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(ticker, start, end);
    }

    /**
     * Load all CSV files from the given directory.  Skips dates already present in the DB.
     * @return summary map with keys "filesLoaded" and "recordsLoaded"
     */
    public Map<String, Object> loadAllCsvFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Directory does not exist: " + directory);
        }

        List<Path> csvFiles;
        try (Stream<Path> stream = Files.list(directory)) {
            csvFiles = stream
                    .filter(p -> p.toString().endsWith(".csv"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        int filesLoaded = 0;
        int recordsLoaded = 0;

        for (Path csvFile : csvFiles) {
            String filename = csvFile.getFileName().toString();
            String dateStr = filename.replace(".csv", "");
            try {
                LocalDate tradeDate = LocalDate.parse(dateStr, DATE_FMT);
                if (dailyPriceRepository.existsByTradeDate(tradeDate)) {
                    log.info("Skipping {} (already loaded)", filename);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Skipping file with non-date name: {}", filename);
                continue;
            }

            int count = loadCsvFile(csvFile);
            filesLoaded++;
            recordsLoaded += count;
            log.info("Loaded {} records from {}", count, filename);
        }

        return Map.of("filesLoaded", filesLoaded, "recordsLoaded", recordsLoaded);
    }

    /**
     * Parse a single CSV and upsert all rows into the daily_prices table.
     */
    public int loadCsvFile(Path csvFile) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        Iterator<Map<String, String>> rows = csvMapper
                .readerForMapOf(String.class)
                .with(schema)
                .readValues(csvFile.toFile());

        List<DailyPrice> batch = new ArrayList<>();

        while (rows.hasNext()) {
            Map<String, String> row = rows.next();
            String symbol = row.get("symbol");
            LocalDate tradeDate = LocalDate.parse(row.get("date"), DATE_FMT);

            DailyPrice price = dailyPriceRepository
                    .findByTickerAndTradeDate(symbol, tradeDate)
                    .orElseGet(DailyPrice::new);

            price.setTicker(symbol);
            price.setTradeDate(tradeDate);
            price.setOpenPrice(parseDouble(row.get("open")));
            price.setHighPrice(parseDouble(row.get("high")));
            price.setLowPrice(parseDouble(row.get("low")));
            price.setClosePrice(parseDouble(row.get("close")));
            price.setVolume(parseLong(row.get("volume")));

            batch.add(price);
        }

        dailyPriceRepository.saveAll(batch);
        return batch.size();
    }

    private Double parseDouble(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return null; }
    }

    private Long parseLong(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }
}

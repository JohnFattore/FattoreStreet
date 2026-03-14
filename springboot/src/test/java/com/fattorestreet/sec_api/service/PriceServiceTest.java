package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private PriceService priceService;

    @Test
    void getPrices_delegatesToRepository() {
        DailyPrice dp = new DailyPrice();
        dp.setTicker("AAPL");
        dp.setTradeDate(LocalDate.of(2025, 6, 15));
        dp.setClosePrice(175.0);

        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(dp));

        List<DailyPrice> result = priceService.getPrices("AAPL");

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).getTicker());
        verify(dailyPriceRepository).findByTickerOrderByTradeDateDesc("AAPL");
    }

    @Test
    void getPrices_withDateRange_delegatesToRepository() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);

        when(dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("AAPL", start, end))
                .thenReturn(List.of());

        List<DailyPrice> result = priceService.getPrices("AAPL", start, end);

        assertTrue(result.isEmpty());
        verify(dailyPriceRepository).findByTickerAndTradeDateBetweenOrderByTradeDateDesc("AAPL", start, end);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadCsvFile_parsesAndSavesRecords(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("2025-03-15.csv");
        Files.writeString(csv, """
                date,symbol,open,high,low,close,volume
                2025-03-15,AAPL,172.5000,174.2000,171.8000,173.9000,45230
                2025-03-15,MSFT,410.0000,415.5000,409.0000,413.2000,31000
                """);

        when(dailyPriceRepository.findByTickerAndTradeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailyPriceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int count = priceService.loadCsvFile(csv);

        assertEquals(2, count);

        ArgumentCaptor<List<DailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).saveAll(captor.capture());
        List<DailyPrice> saved = captor.getValue();

        assertEquals(2, saved.size());
        assertEquals("AAPL", saved.get(0).getTicker());
        assertEquals(LocalDate.of(2025, 3, 15), saved.get(0).getTradeDate());
        assertEquals(172.5, saved.get(0).getOpenPrice());
        assertEquals(174.2, saved.get(0).getHighPrice());
        assertEquals(171.8, saved.get(0).getLowPrice());
        assertEquals(173.9, saved.get(0).getClosePrice());
        assertEquals(45230L, saved.get(0).getVolume());
    }

    @Test
    void loadAllCsvFiles_skipsAlreadyLoadedDates(@TempDir Path tempDir) throws IOException {
        Path csv1 = tempDir.resolve("2025-03-14.csv");
        Path csv2 = tempDir.resolve("2025-03-15.csv");
        Files.writeString(csv1, "date,symbol,open,high,low,close,volume\n2025-03-14,AAPL,170.0,171.0,169.0,170.5,1000\n");
        Files.writeString(csv2, "date,symbol,open,high,low,close,volume\n2025-03-15,AAPL,172.0,174.0,171.0,173.0,2000\n");

        when(dailyPriceRepository.existsByTradeDate(LocalDate.of(2025, 3, 14))).thenReturn(true);
        when(dailyPriceRepository.existsByTradeDate(LocalDate.of(2025, 3, 15))).thenReturn(false);
        when(dailyPriceRepository.findByTickerAndTradeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailyPriceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = priceService.loadAllCsvFiles(tempDir);

        assertEquals(1, result.get("filesLoaded"));
        assertEquals(1, result.get("recordsLoaded"));
    }

    @Test
    void loadAllCsvFiles_throwsForNonExistentDirectory() {
        Path noDir = Path.of("/tmp/does-not-exist-" + System.nanoTime());
        assertThrows(IOException.class, () -> priceService.loadAllCsvFiles(noDir));
    }

    @Test
    void loadCsvFile_updatesExistingRecord(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("2025-03-15.csv");
        Files.writeString(csv, "date,symbol,open,high,low,close,volume\n2025-03-15,AAPL,172.5,174.2,171.8,173.9,45230\n");

        DailyPrice existing = new DailyPrice();
        existing.setId(42L);
        existing.setTicker("AAPL");
        existing.setTradeDate(LocalDate.of(2025, 3, 15));
        existing.setClosePrice(170.0);

        when(dailyPriceRepository.findByTickerAndTradeDate("AAPL", LocalDate.of(2025, 3, 15)))
                .thenReturn(Optional.of(existing));
        when(dailyPriceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        priceService.loadCsvFile(csv);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).saveAll(captor.capture());
        DailyPrice updated = captor.getValue().get(0);

        assertEquals(42L, updated.getId());
        assertEquals(173.9, updated.getClosePrice());
    }
}

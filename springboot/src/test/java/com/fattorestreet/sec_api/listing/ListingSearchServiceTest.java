package com.fattorestreet.sec_api.listing;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.listing.ListingSearchService.SearchResult;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fattorestreet.sec_api.repository.ListingRepository.SearchRow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSearchServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingSearchService listingSearchService;

    private record Row(String getTicker, String getTitle, String getSecSeriesName, Boolean getIsFund)
            implements SearchRow {
    }

    @Test
    void normalizesQueryIntoNameAndTickerForms() {
        when(listingRepository.searchByNameOrTicker(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyInt())).thenReturn(List.of());

        listingSearchService.search("  Brk.b   Class ", 10, true);

        verify(listingRepository).searchByNameOrTicker(
                "brk.b class", "BRK-B CLASS", "BRK-B CLASS%", "brk.b class%", "%brk.b class%", true, 10);
    }

    @Test
    void lowersSimilarityThresholdBeforeSearching() {
        when(listingRepository.searchByNameOrTicker(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyInt())).thenReturn(List.of());

        listingSearchService.search("micrsoft", 10, true);

        var order = inOrder(listingRepository);
        order.verify(listingRepository).setWordSimilarityThreshold(ListingSearchService.WORD_SIMILARITY_THRESHOLD);
        order.verify(listingRepository).searchByNameOrTicker(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyInt());
    }

    @Test
    void escapesLikeWildcardsInPatterns() {
        when(listingRepository.searchByNameOrTicker(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyInt())).thenReturn(List.of());

        listingSearchService.search("50%_off", 5, false);

        verify(listingRepository).searchByNameOrTicker(
                "50%_off", "50%_OFF", "50\\%\\_OFF%", "50\\%\\_off%", "%50\\%\\_off%", false, 5);
    }

    @Test
    void blankQueryReturnsNothingWithoutQuerying() {
        assertTrue(listingSearchService.search("   ", 10, true).isEmpty());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void prefersFundSeriesNameAndHidesNamesThatAreJustTheTicker() {
        when(listingRepository.searchByNameOrTicker(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyInt())).thenReturn(List.of(
                new Row("AAPL", "Apple Inc.", null, false),
                new Row("VTSAX", "VANGUARD INDEX FUNDS", "Vanguard Total Stock Market Index Fund", true),
                new Row("VTI", "VTI", null, true),
                new Row("XYZ", "  ", " ", null)));

        List<SearchResult> results = listingSearchService.search("v", 10, true);

        assertEquals(List.of(
                new SearchResult("AAPL", "Apple Inc.", false),
                new SearchResult("VTSAX", "Vanguard Total Stock Market Index Fund", true),
                new SearchResult("VTI", null, true),
                new SearchResult("XYZ", null, false)), results);
    }
}

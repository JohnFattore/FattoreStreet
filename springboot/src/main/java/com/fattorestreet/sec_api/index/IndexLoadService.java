package com.fattorestreet.sec_api.index;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the two-phase index load shared by {@code AdminController} and {@link IndexLoadRunner}:
 * a metrics refresh ({@link IndexMetricsRefreshService}) followed by cap-ranked rebuilds of every
 * configured Fattore index in canonical order.
 */
@Service
public class IndexLoadService {

    private final IndexMetricsRefreshService indexMetricsRefreshService;
    private final List<FattoreIndexRebuildService> rebuildAllOrdered;

    public IndexLoadService(
            IndexMetricsRefreshService indexMetricsRefreshService,
            @Qualifier("fattore50IndexRebuildService") FattoreIndexRebuildService fattore50IndexRebuildService,
            @Qualifier("fattore100IndexRebuildService") FattoreIndexRebuildService fattore100IndexRebuildService,
            @Qualifier("fattore1000IndexRebuildService") FattoreIndexRebuildService fattore1000IndexRebuildService) {
        this.indexMetricsRefreshService = indexMetricsRefreshService;
        // Lexicographic by market index code: FAT100, FAT1000, FAT50
        this.rebuildAllOrdered = List.of(
                fattore100IndexRebuildService, fattore1000IndexRebuildService, fattore50IndexRebuildService);
    }

    /**
     * Runs the metrics refresh for the given scope: {@code russell1000}/{@code iwb} (IWB holdings
     * universe) or {@code all} (every listing in the database).
     *
     * @throws IllegalArgumentException for an unknown scope
     */
    public IndexMetricsRefreshService.RefreshResult refresh(String scope, int year) {
        String normalized = scope != null ? scope.trim().toLowerCase(Locale.ROOT) : "russell1000";
        return switch (normalized) {
            case "russell1000", "iwb" -> indexMetricsRefreshService.refreshRussell1000Listings(year);
            case "all" -> indexMetricsRefreshService.refreshAllTickers(year);
            default -> throw new IllegalArgumentException(
                    "Unknown scope: " + scope + ". Supported: russell1000 (iwb), all");
        };
    }

    /**
     * Rebuilds all configured cap-ranked indexes in canonical order (FAT100, FAT1000, FAT50).
     */
    public List<FattoreIndexRebuildService.RebuildResult> rebuildAll(int year) {
        return rebuildAllOrdered.stream()
                .map(service -> service.rebuild(year))
                .toList();
    }
}

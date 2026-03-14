package com.fattorestreet.sec_api.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class CorporateActionSchemaReconciler {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionSchemaReconciler.class);
    private static final String LOOKUP_INDEX_NAME = "idx_corp_actions_ticker_type_date";
    private static final String UNIQUE_CONSTRAINT_NAME = "uk_corp_actions_ticker_type_date_ratio";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public CorporateActionSchemaReconciler(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileCorporateActionIndexes() {
        if (!isPostgres()) {
            return;
        }
        try {
            dropLegacyThreeColumnUniqueConstraints();
            ensureFourColumnUniqueConstraint();
            ensureLookupIndex();
        } catch (Exception e) {
            log.warn("Corporate action schema reconciliation skipped: {}", e.getMessage());
        }
    }

    private void dropLegacyThreeColumnUniqueConstraints() {
        List<String> legacyConstraintNames = jdbcTemplate.queryForList("""
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = 'corporate_actions'
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                HAVING COUNT(*) = 3
                   AND SUM(CASE WHEN kcu.column_name = 'ticker' THEN 1 ELSE 0 END) = 1
                   AND SUM(CASE WHEN kcu.column_name = 'action_type' THEN 1 ELSE 0 END) = 1
                   AND SUM(CASE WHEN kcu.column_name = 'effective_date' THEN 1 ELSE 0 END) = 1
                """, String.class);
        for (String constraintName : legacyConstraintNames) {
            if (!constraintName.matches("[a-zA-Z0-9_]+")) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE corporate_actions DROP CONSTRAINT IF EXISTS " + constraintName);
            log.info("Dropped legacy corporate_actions constraint {}", constraintName);
        }
    }

    private void ensureFourColumnUniqueConstraint() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM (
                    SELECT tc.constraint_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                    WHERE tc.table_schema = 'public'
                      AND tc.table_name = 'corporate_actions'
                      AND tc.constraint_type = 'UNIQUE'
                    GROUP BY tc.constraint_name
                    HAVING COUNT(*) = 4
                       AND SUM(CASE WHEN kcu.column_name = 'ticker' THEN 1 ELSE 0 END) = 1
                       AND SUM(CASE WHEN kcu.column_name = 'action_type' THEN 1 ELSE 0 END) = 1
                       AND SUM(CASE WHEN kcu.column_name = 'effective_date' THEN 1 ELSE 0 END) = 1
                       AND SUM(CASE WHEN kcu.column_name = 'ratio' THEN 1 ELSE 0 END) = 1
                ) matching
                """, Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE corporate_actions ADD CONSTRAINT " + UNIQUE_CONSTRAINT_NAME
                + " UNIQUE (ticker, action_type, effective_date, ratio)");
        log.info("Added corporate_actions unique constraint {}", UNIQUE_CONSTRAINT_NAME);
    }

    private void ensureLookupIndex() {
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS " + LOOKUP_INDEX_NAME
                        + " ON corporate_actions (ticker, action_type, effective_date)");
        log.info("Ensured corporate_actions lookup index {}", LOOKUP_INDEX_NAME);
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            return false;
        }
    }
}

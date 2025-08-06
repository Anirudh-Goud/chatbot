package com.llama.chatbot.service;

import com.llama.chatbot.service.exception.DatabaseServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SqlExecutionService {

    private final JdbcTemplate jdbcTemplate;

    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
            "(?i)(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|MERGE|REPLACE)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    public SqlExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueryResult executeSafeQuery(String sql) {
        try {
            validateQuery(sql);
            log.info("Executing validated SQL query: {}", sql);
            List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                return row;
            });

            return QueryResult.builder()
                    .success(true)
                    .data(rows)
                    .rowCount(rows.size())
                    .columns(extractColumns(rows))
                    .executedSql(sql)
                    .build();

        } catch (Exception e) {
            log.error("Error executing SQL query: {}", sql, e);
            // Re-throw as our custom exception
            throw new DatabaseServiceException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    // --- (validateQuery and other methods are mostly unchanged but could throw our custom exceptions) ---
    private void validateQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty. It may be that the AI failed to generate a query.");
        }
        if (DANGEROUS_PATTERNS.matcher(sql).find()) {
            throw new IllegalArgumentException("Query contains potentially dangerous operations (e.g., DROP, DELETE). Only SELECT statements are allowed.");
        }
        String trimmedSql = sql.trim();
        if (!trimmedSql.toLowerCase().startsWith("select") && !trimmedSql.toLowerCase().startsWith("with")) {
            throw new IllegalArgumentException("Only SELECT and WITH statements are allowed.");
        }
    }

    private List<String> extractColumns(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rows.get(0).keySet());
    }

    public boolean testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("Database connection test failed", e);
            return false;
        }
    }

    public List<String> getTableNames() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                    String.class
            );
        } catch (Exception e) {
            log.error("Error fetching table names", e);
            throw new DatabaseServiceException("Could not fetch table names.", e);
        }
    }

    public Map<String, Object> getTableInfo(String tableName) {
        try {
            if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                throw new IllegalArgumentException("Invalid table name format.");
            }
            String sql = "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = ? AND table_schema = 'public' ORDER BY ordinal_position";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
            Map<String, Object> tableInfo = new HashMap<>();
            tableInfo.put("table_name", tableName);
            tableInfo.put("columns", columns);
            return tableInfo;
        } catch (Exception e) {
            log.error("Error fetching table info for table: {}", tableName, e);
            throw new DatabaseServiceException("Could not fetch table info for " + tableName, e);
        }
    }

    // --- QueryResult DTO (Unchanged) ---
    public static class QueryResult {
        private boolean success;
        private List<Map<String, Object>> data;
        private List<String> columns;
        private int rowCount;
        private String error;
        private String executedSql;

        public static QueryResultBuilder builder() {
            return new QueryResultBuilder();
        }

        public boolean isSuccess() { return success; }
        public List<Map<String, Object>> getData() { return data; }
        public List<String> getColumns() { return columns; }
        public int getRowCount() { return rowCount; }
        public String getError() { return error; }
        public String getExecutedSql() { return executedSql; }

        public static class QueryResultBuilder {
            private boolean success;
            private List<Map<String, Object>> data;
            private List<String> columns;
            private int rowCount;
            private String error;
            private String executedSql;

            public QueryResultBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public QueryResultBuilder data(List<Map<String, Object>> data) {
                this.data = data;
                return this;
            }

            public QueryResultBuilder columns(List<String> columns) {
                this.columns = columns;
                return this;
            }

            public QueryResultBuilder rowCount(int rowCount) {
                this.rowCount = rowCount;
                return this;
            }

            public QueryResultBuilder error(String error) {
                this.error = error;
                return this;
            }

            public QueryResultBuilder executedSql(String executedSql) {
                this.executedSql = executedSql;
                return this;
            }

            public QueryResult build() {
                QueryResult result = new QueryResult();
                result.success = this.success;
                result.data = this.data;
                result.columns = this.columns;
                result.rowCount = this.rowCount;
                result.error = this.error;
                result.executedSql = this.executedSql;
                return result;
            }
        }
    }
}
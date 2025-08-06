package com.llama.chatbot.service;

import com.llama.chatbot.service.exception.DatabaseServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DatabaseSchemaService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates a detailed text representation of the database schema.
     * This context is crucial for preventing AI hallucination.
     * @return A string describing tables and their columns.
     */
    public String getDetailedSchemaContext() {
        log.info("Fetching detailed schema context for the AI prompt.");
        try {
            List<String> tableNames = getTableNames();
            StringBuilder schemaBuilder = new StringBuilder();

            for (String tableName : tableNames) {
                schemaBuilder.append(String.format("Table `%s`:\n", tableName));
                List<Map<String, Object>> columns = getTableColumns(tableName);
                for (Map<String, Object> column : columns) {
                    String columnName = (String) column.get("column_name");
                    String dataType = (String) column.get("data_type");
                    schemaBuilder.append(String.format("  - %s (%s)\n", columnName, dataType));
                }
                schemaBuilder.append("\n");
            }

            String detailedSchema = schemaBuilder.toString();
            log.debug("Generated Schema Context:\n{}", detailedSchema);
            return detailedSchema;

        } catch (Exception e) {
            log.error("Failed to generate detailed schema context.", e);
            throw new DatabaseServiceException("Could not generate schema context for AI.", e);
        }
    }

    private List<String> getTableNames() {
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class
        );
    }

    private List<Map<String, Object>> getTableColumns(String tableName) {
        // Basic validation to prevent SQL injection in table name
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid table name format");
        }
        String sql = "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ? AND table_schema = 'public' ORDER BY ordinal_position";
        return jdbcTemplate.queryForList(sql, tableName);
    }
}
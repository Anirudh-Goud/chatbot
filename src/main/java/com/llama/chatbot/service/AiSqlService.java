package com.llama.chatbot.service;

import com.llama.chatbot.service.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiSqlService {

    private final OllamaService ollamaService;
    private final DatabaseSchemaService schemaService; // Use the new service
    private final SqlExecutionService sqlExecutionService;

    public AiSqlService(OllamaService ollamaService,
                        DatabaseSchemaService schemaService, // Inject the new service
                        SqlExecutionService sqlExecutionService) {
        this.ollamaService = ollamaService;
        this.schemaService = schemaService;
        this.sqlExecutionService = sqlExecutionService;
    }

    public AiQueryResponse processNaturalLanguageQuery(String naturalQuery) {
        log.info("Processing natural language query: {}", naturalQuery);
        String generatedSql;
        try {
            // CRITICAL FIX: Get the DETAILED schema, not just a simple context.
            String schemaContext = schemaService.getDetailedSchemaContext();
            generatedSql = ollamaService.generateSql(naturalQuery, schemaContext);

            if (generatedSql == null || generatedSql.trim().isEmpty()) {
                throw new AiServiceException("AI failed to generate a valid SQL query. The response was empty.");
            }
            log.info("Generated SQL: {}", generatedSql);

        } catch (AiServiceException e) {
            log.error("AI service failed to generate SQL for query: '{}'", naturalQuery, e);
            // Re-throw AI exceptions directly
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during SQL generation for query: '{}'", naturalQuery, e);
            throw new AiServiceException("An unexpected error occurred while preparing the AI request.", e);
        }

        // Execute the generated query
        SqlExecutionService.QueryResult result = sqlExecutionService.executeSafeQuery(generatedSql);

        return AiQueryResponse.builder()
                .originalQuery(naturalQuery)
                .generatedSql(generatedSql)
                .executionResult(result)
                .build();
    }

    public AiQueryResponse refineSqlQuery(String originalSql, String refinementRequest) {
        // Similar update for refine
        String schemaContext = schemaService.getDetailedSchemaContext();
        // The ollamaService.generateSql needs to be adapted or a new method created if it should handle refinement differently
        String refinedSql = ollamaService.refineSql(originalSql, refinementRequest, schemaContext);

        if (refinedSql == null || refinedSql.trim().isEmpty()) {
            throw new AiServiceException("AI failed to generate a refined SQL query.");
        }

        SqlExecutionService.QueryResult result = sqlExecutionService.executeSafeQuery(refinedSql);

        return AiQueryResponse.builder()
                .originalQuery(refinementRequest)
                .generatedSql(refinedSql)
                .executionResult(result)
                .previousSql(originalSql)
                .build();
    }

    public String explainSqlQuery(String sql) {
        String schemaContext = schemaService.getDetailedSchemaContext();
        // The ollamaService.generateSql needs to be adapted or a new method created for explanation
        String explanation = ollamaService.explainSql(sql, schemaContext);

        if (explanation == null || explanation.trim().isEmpty()) {
            throw new AiServiceException("AI failed to generate an explanation.");
        }
        return explanation;
    }

    public boolean isServiceHealthy() {
        boolean ollamaIsOk = ollamaService.isHealthy();
        boolean dbIsOk = sqlExecutionService.testConnection();
        return ollamaIsOk && dbIsOk;
    }

    // --- Inner DTO Class (No Changes Needed) ---
    public static class AiQueryResponse {
        private String originalQuery;
        private String generatedSql;
        private String previousSql;
        private SqlExecutionService.QueryResult executionResult;
        private boolean success;
        private String error;

        public static AiQueryResponseBuilder builder() {
            return new AiQueryResponseBuilder();
        }

        public boolean isSuccess() {
            // Success is determined by the execution result
            return executionResult != null && executionResult.isSuccess();
        }

        public String getOriginalQuery() { return originalQuery; }
        public String getGeneratedSql() { return generatedSql; }
        public String getPreviousSql() { return previousSql; }
        public SqlExecutionService.QueryResult getExecutionResult() { return executionResult; }
        public String getError() { return error; }

        public static class AiQueryResponseBuilder {
            private String originalQuery;
            private String generatedSql;
            private String previousSql;
            private SqlExecutionService.QueryResult executionResult;
            private String error;

            public AiQueryResponseBuilder originalQuery(String o) { this.originalQuery = o; return this; }
            public AiQueryResponseBuilder generatedSql(String s) { this.generatedSql = s; return this; }
            public AiQueryResponseBuilder previousSql(String p) { this.previousSql = p; return this; }
            public AiQueryResponseBuilder executionResult(SqlExecutionService.QueryResult r) { this.executionResult = r; return this; }
            public AiQueryResponseBuilder error(String e) { this.error = e; return this; }

            public AiQueryResponse build() {
                AiQueryResponse response = new AiQueryResponse();
                response.originalQuery = this.originalQuery;
                response.generatedSql = this.generatedSql;
                response.previousSql = this.previousSql;
                response.executionResult = this.executionResult;
                response.error = this.error; // Keep error separate from executionResult's error
                return response;
            }
        }
    }
}
package com.llama.chatbot.controller;

import com.llama.chatbot.service.AiSqlService;
import com.llama.chatbot.service.SqlExecutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sql")
@CrossOrigin(origins = "*")
@Slf4j
public class SqlController {

    private final AiSqlService aiSqlService;
    private final SqlExecutionService sqlExecutionService;

    public SqlController(AiSqlService aiSqlService, SqlExecutionService sqlExecutionService) {
        this.aiSqlService = aiSqlService;
        this.sqlExecutionService = sqlExecutionService;
    }

    /**
     * RECTIFIED: The if/else block is removed. If any exception occurs in the service,
     * the GlobalExceptionHandler will now catch it and return a proper error response.
     * This makes the controller's "happy path" code cleaner and more concise.
     */
    @PostMapping("/generate")
    public ResponseEntity<AiSqlService.AiQueryResponse> generateSql(@Valid @RequestBody QueryRequest request) {
        log.info("Received natural language query: {}", request.getQuery());
        AiSqlService.AiQueryResponse response = aiSqlService.processNaturalLanguageQuery(request.getQuery());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refine")
    public ResponseEntity<AiSqlService.AiQueryResponse> refineSql(@Valid @RequestBody RefineRequest request) {
        log.info("Refining SQL query with request: {}", request.getRefinementRequest());
        AiSqlService.AiQueryResponse response = aiSqlService.refineSqlQuery(
                request.getOriginalSql(),
                request.getRefinementRequest()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    public ResponseEntity<SqlExecutionService.QueryResult> executeSql(@Valid @RequestBody ExecuteRequest request) {
        log.info("Executing SQL query: {}", request.getSql());
        SqlExecutionService.QueryResult result = sqlExecutionService.executeSafeQuery(request.getSql());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/explain")
    public ResponseEntity<Map<String, String>> explainSql(@Valid @RequestBody ExecuteRequest request) {
        log.info("Explaining SQL query: {}", request.getSql());
        String explanation = aiSqlService.explainSqlQuery(request.getSql());
        return ResponseEntity.ok(Map.of("explanation", explanation));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean dbConnection = sqlExecutionService.testConnection();
        boolean aiConnection = aiSqlService.isServiceHealthy();
        boolean isOverallHealthy = dbConnection && aiConnection;

        Map<String, Object> health = Map.of(
                "status", isOverallHealthy ? "HEALTHY" : "UNHEALTHY",
                "database_connection", dbConnection,
                "ai_service_connection", aiConnection
        );
        return ResponseEntity.ok(health);
    }

    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> getTables() {
        var tables = sqlExecutionService.getTableNames();
        return ResponseEntity.ok(Map.of("tables", tables));
    }

    @GetMapping("/tables/{tableName}")
    public ResponseEntity<Map<String, Object>> getTableInfo(@PathVariable String tableName) {
        var tableInfo = sqlExecutionService.getTableInfo(tableName);
        return ResponseEntity.ok(Map.of("table_name", tableName, "details", tableInfo));
    }

    // --- DTOs (No changes needed) ---
    public static class QueryRequest {
        @NotBlank(message = "Query cannot be empty")
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    public static class RefineRequest {
        @NotBlank(message = "Original SQL cannot be empty")
        private String originalSql;
        @NotBlank(message = "Refinement request cannot be empty")
        private String refinementRequest;
        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public String getRefinementRequest() { return refinementRequest; }
        public void setRefinementRequest(String refinementRequest) { this.refinementRequest = refinementRequest; }
    }

    public static class ExecuteRequest {
        @NotBlank(message = "SQL cannot be empty")
        private String sql;
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
    }
}
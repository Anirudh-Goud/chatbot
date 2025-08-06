package com.llama.chatbot.service;

import com.llama.chatbot.service.dto.OllamaChatRequest;
import com.llama.chatbot.service.dto.OllamaChatResponse;
import com.llama.chatbot.service.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class OllamaService {

    private final WebClient webClient;
    private final String ollamaModel;
    private final Duration requestTimeout;

    @Autowired
    public OllamaService(@Qualifier("ollamaWebClient") WebClient webClient,
                         @Value("${ollama.model}") String ollamaModel,
                         @Value("${ollama.timeout:60}") long timeoutSeconds) {
        this.webClient = webClient;
        this.ollamaModel = ollamaModel;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * REFACTORED: Now uses the helper method to generate SQL.
     */
    public String generateSql(String naturalQuery, String schemaContext) {
        String prompt = buildSqlPrompt(naturalQuery, schemaContext);
        return callOllamaChat(prompt, "SQL Generation");
    }

    /**
     * NEW METHOD: Handles refining an existing query by creating a specific prompt.
     */
    public String refineSql(String originalSql, String refinementRequest, String schemaContext) {
        String prompt = String.format("""
            You are an expert PostgreSQL query editor. Your task is to modify an existing SQL query based on a user's request.

            ### Rules:
            1. ONLY output the raw, modified SQL query. Do not include explanations or markdown.
            
            ### Schema Context:
            %s
            
            ### Original SQL Query:
            %s
            
            ### User's Refinement Request:
            "%s"
            
            Modified SQL Query:
            """, schemaContext, originalSql, refinementRequest);

        return callOllamaChat(prompt, "SQL Refinement");
    }

    /**
     * NEW METHOD: Handles explaining an existing query by creating a specific prompt.
     */
    public String explainSql(String sql, String schemaContext) {
        String prompt = String.format("""
            You are an expert at explaining PostgreSQL queries. Your task is to explain the provided SQL query in simple, clear terms.
            Do not include the original query in your response. Just provide the explanation.

            ### Schema Context:
            %s
            
            ### SQL Query to Explain:
            %s
            
            ### Explanation:
            """, schemaContext, sql);

        return callOllamaChat(prompt, "SQL Explanation");
    }

    /**
     * NEW private helper method to avoid code duplication.
     * This method contains the core logic for communicating with the Ollama /api/chat endpoint.
     */
    private String callOllamaChat(String prompt, String taskType) {
        OllamaChatRequest request = new OllamaChatRequest(
                ollamaModel,
                List.of(new OllamaChatRequest.Message("user", prompt)),
                false // We want the full response, not a stream
        );

        try {
            OllamaChatResponse response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaChatResponse.class)
                    .timeout(requestTimeout)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                    .block();

            if (response == null || response.getMessage() == null || response.getMessage().getContent() == null) {
                throw new AiServiceException("Received an empty or invalid response from Ollama for task: " + taskType);
            }
            return cleanSqlResponse(response.getMessage().getContent());
        } catch (Exception e) {
            log.error("Error communicating with Ollama for task: {}. Check model '{}'. Exception: {}", taskType, ollamaModel, e.getMessage());
            throw new AiServiceException("Failed to get a response from the AI service for " + taskType + ". Is Ollama running and is the model '" + ollamaModel + "' pulled?", e);
        }
    }

    private String buildSqlPrompt(String query, String schemaContext) {
        return String.format(
                """
                You are an expert PostgreSQL query generator. Based on the following database schema and a natural language query,
                generate a single, executable SQL query.
    
                ### Rules:
                1. ONLY output the raw SQL query. Do not include any other text, explanations, or markdown formatting like ```sql.
                2. The query must be compatible with PostgreSQL and PostGIS.
                3. Use the provided schema context to ensure correct table and column names.
    
                ### Schema Context:
                %s
    
                ### Natural Language Request:
                "%s"
    
                SQL Query:
                """,
                schemaContext,
                query
        );
    }

    private String cleanSqlResponse(String rawResponse) {
        return rawResponse.replace("```sql", "")
                .replace("```", "")
                .trim();
    }

    public boolean isHealthy() {
        try {
            webClient.get().uri("/").retrieve().toBodilessEntity().timeout(Duration.ofSeconds(5)).block();
            return true;
        } catch (Exception e) {
            log.warn("Ollama service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
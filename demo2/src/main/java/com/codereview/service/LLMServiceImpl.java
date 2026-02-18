package com.codereview.service;

import com.codereview.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM Service Implementation using Groq API
 *
 * ARCHITECTURE DECISION: Why Java 11+ HttpClient?
 * - Built into Java (no external dependency)
 * - Supports async calls (if needed later)
 * - Modern, cleaner API than HttpURLConnection
 * - Works without Spring WebClient
 */
@Service
public class LLMServiceImpl implements LLMService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiKey;
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    /**
     * Constructor
     * WHY: Inject API key from config, create reusable HTTP client
     */
    public LLMServiceImpl(@Value("${groq.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        System.out.println("[LLM] Initialized with Groq API");
    }

    /**
     * PLANNING AGENT IMPLEMENTATION
     *
     * PROMPT ENGINEERING STRATEGY:
     * - Be specific about output format (JSON)
     * - Give examples of good issue descriptions
     * - Ask for actionable fix plan
     */
    @Override
    public void analyzeAndPlan(CodeReviewState state) {
        System.out.println("[LLM] PLANNING AGENT: Analyzing code...");

        String prompt = String.format("""
            You are a code review expert. Analyze the following %s code and:
            
            1. Identify ALL issues (syntax errors, logic bugs, bad practices, potential runtime errors)
            2. For each issue, specify:
               - Line number (approximate if needed)
               - Severity (error/warning/info)
               - Category (syntax/logic/performance/style)
               - Clear description
            3. Create a fix plan explaining your strategy
            
            Return ONLY valid JSON in this exact format:
            {
              "issues": [
                {
                  "description": "Variable 'x' is undefined",
                  "lineNumber": 5,
                  "severity": "error",
                  "category": "syntax"
                }
              ],
              "fixPlan": "First fix syntax errors, then address logic issues..."
            }
            
            CODE TO ANALYZE:
```%s
            %s
```
            
            IMPORTANT: Return ONLY the JSON, no markdown, no explanation.
            """,
                state.getLanguage(),
                state.getLanguage(),
                state.getOriginalCode()
        );

        try {
            String response = callLLM(prompt, 0.3); // Low temperature for structured output
            parsePlanningResponse(response, state);
            System.out.println("[LLM] PLANNING COMPLETE: Found " + state.getIdentifiedIssues().size() + " issues");
        } catch (Exception e) {
            System.err.println("[LLM] Planning failed: " + e.getMessage());
            throw new RuntimeException("Failed to analyze code", e);
        }
    }

    /**
     * Parse LLM's planning response into state
     *
     * WHY SEPARATE METHOD: Error handling and parsing logic isolated
     */
    private void parsePlanningResponse(String response, CodeReviewState state) {
        try {
            // Clean up response (LLM sometimes adds markdown)
            String cleanJson = response
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode root = mapper.readTree(cleanJson);

            // Parse issues
            List<Issue> issues = new ArrayList<>();
            JsonNode issuesArray = root.path("issues");
            for (JsonNode issueNode : issuesArray) {
                Issue issue = new Issue(
                        issueNode.path("description").asText(),
                        issueNode.path("lineNumber").asInt(0),
                        issueNode.path("severity").asText("error"),
                        issueNode.path("category").asText("unknown")
                );
                issues.add(issue);
            }

            state.setIdentifiedIssues(issues);
            state.setFixPlan(root.path("fixPlan").asText());

        } catch (Exception e) {
            System.err.println("[LLM] Failed to parse planning response: " + response);
            throw new RuntimeException("Invalid JSON from LLM", e);
        }
    }

    /**
     * EXECUTOR AGENT IMPLEMENTATION
     *
     * PROMPT ENGINEERING STRATEGY:
     * - Provide context: original code, issues found, fix plan
     * - Include history: what was tried before (if any)
     * - Demand only code output (no explanations in the code block)
     */
    @Override
    public String generateFixedCode(CodeReviewState state, String reasoning) {
        System.out.println("[LLM] EXECUTOR AGENT: Generating fix attempt " + (state.getCurrentAttemptNumber() + 1));

        // Build context from previous attempts
        StringBuilder attemptHistory = new StringBuilder();
        if (!state.getAttempts().isEmpty()) {
            attemptHistory.append("\nPREVIOUS ATTEMPTS (learn from these):\n");
            for (Attempt attempt : state.getAttempts()) {
                attemptHistory.append(String.format("""
                    Attempt %d:
                    - Reasoning: %s
                    - Result: %s
                    - Error: %s
                    
                    """,
                        attempt.getAttemptNumber(),
                        attempt.getReasoning(),
                        attempt.getExecutionResult().isSuccess() ? "SUCCESS" : "FAILED",
                        attempt.getExecutionResult().getStderr()
                ));
            }
        }

        String prompt = String.format("""
            You are fixing %s code. Here's the situation:
            
            ORIGINAL CODE:
```%s
            %s
```
            
            ISSUES IDENTIFIED:
            %s
            
            FIX PLAN:
            %s
            
            %s
            
            CURRENT REASONING:
            %s
            
            YOUR TASK:
            Write the FIXED code. Return ONLY the code, nothing else.
            - Fix all identified issues
            - Keep the same functionality
            - Make sure it's syntactically correct
            - Don't add comments explaining changes (just fix it)
            
            RETURN FORMAT: Just the code, no markdown, no explanations.
            """,
                state.getLanguage(),
                state.getLanguage(),
                state.getOriginalCode(),
                formatIssues(state.getIdentifiedIssues()),
                state.getFixPlan(),
                attemptHistory.toString(),
                reasoning
        );

        try {
            String fixedCode = callLLM(prompt, 0.4); // Slightly higher temp for creativity

            // Clean up markdown if LLM added it
            fixedCode = fixedCode
                    .replaceAll("```" + state.getLanguage() + "\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            System.out.println("[LLM] EXECUTOR COMPLETE: Generated fixed code");
            return fixedCode;

        } catch (Exception e) {
            System.err.println("[LLM] Code generation failed: " + e.getMessage());
            throw new RuntimeException("Failed to generate fixed code", e);
        }
    }

    /**
     * REFLECTION AGENT IMPLEMENTATION
     *
     * PROMPT ENGINEERING STRATEGY:
     * - Show what we tried and what happened
     * - Ask "why did it fail?"
     * - Request specific next steps
     *
     * WHY THIS IS CRITICAL: This is where the "learning" happens
     */
    @Override
    public String reflect(CodeReviewState state) {
        System.out.println("[LLM] REFLECTION AGENT: Analyzing failure...");

        Attempt lastAttempt = state.getLastAttempt();
        if (lastAttempt == null) {
            return "First attempt - no reflection needed yet";
        }

        ExecutionResult result = lastAttempt.getExecutionResult();

        String prompt = String.format("""
            You are debugging why a code fix failed. Analyze this situation:
            
            ORIGINAL ISSUES:
            %s
            
            WHAT WE TRIED (Attempt %d):
            Reasoning: %s
            
            Modified Code:
```%s
            %s
```
            
            EXECUTION RESULT:
            - Success: %s
            - Exit Code: %d
            - Stdout: %s
            - Stderr: %s
            
            YOUR ANALYSIS:
            1. Why did this approach fail?
            2. What was wrong with our reasoning?
            3. What should we try differently next time?
            
            Be specific and actionable. Format: 2-3 sentences.
            """,
                formatIssues(state.getIdentifiedIssues()),
                lastAttempt.getAttemptNumber(),
                lastAttempt.getReasoning(),
                state.getLanguage(),
                lastAttempt.getModifiedCode(),
                result.isSuccess(),
                result.getExitCode(),
                result.getStdout(),
                result.getStderr()
        );

        try {
            String reflection = callLLM(prompt, 0.7); // Higher temp for reasoning
            System.out.println("[LLM] REFLECTION: " + reflection);
            return reflection;

        } catch (Exception e) {
            System.err.println("[LLM] Reflection failed: " + e.getMessage());
            return "Reflection failed: " + e.getMessage();
        }
    }

    /**
     * Core LLM API call
     *
     * WHY SEPARATE METHOD: Reused by all three agents
     *
     * @param prompt The full prompt to send
     * @param temperature Controls randomness (0.0 = deterministic, 1.0 = creative)
     * @return Raw text response from LLM
     */
    private String callLLM(String prompt, double temperature) throws Exception {
        // Build request body
        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", temperature
//                "max_tokens", 2000 should be configurable
        );

        String jsonBody = mapper.writeValueAsString(requestBody);

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
//                .timeout(Duration.ofSeconds(60))
                .build();

        // Send request
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        // Handle errors
        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
        }

        // Parse response
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }

    /**
     * Helper: Format issues for prompt
     */
    private String formatIssues(List<Issue> issues) {
        StringBuilder sb = new StringBuilder();
        for (Issue issue : issues) {
            sb.append(String.format("- Line %d [%s/%s]: %s\n",
                    issue.getLineNumber(),
                    issue.getSeverity(),
                    issue.getCategory(),
                    issue.getDescription()
            ));
        }
        return sb.toString();
    }
}

/**
 Key Design Decisions Explained:

 1. Why One LLM Instead of Three?
 BAD: Create 3 LLM instances
 GOOD: One LLM, three methods with different prompts

 On the other hand we could have use one more LLM for reflection ( for better reasoning )

 WHY:
 - Cheaper (reuse HTTP client)
 - Cleaner state management
 - LLMs are stateless anyway - prompts are what matter
 */

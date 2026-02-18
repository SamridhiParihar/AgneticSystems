package com.codereview.controller;

import com.codereview.model.CodeReviewRequest;
import com.codereview.model.CodeReviewResponse;
import com.codereview.orchestrator.CodeReviewOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for Code Review Agent
 *
 * ENDPOINTS:
 * POST /api/review - Submit code for review and fixing
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow CORS for frontend access
public class CodeReviewController {

    private final CodeReviewOrchestrator orchestrator;

    public CodeReviewController(CodeReviewOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Main endpoint: Review and fix code
     *
     * REQUEST BODY:
     * {
     *   "code": "def hello():\n  print('hello')",
     *   "language": "python"
     * }
     *
     * RESPONSE:
     * {
     *   "reviewId": "abc-123",
     *   "issuesFound": [...],
     *   "fixPlan": "...",
     *   "attempts": [...],
     *   "success": true,
     *   "finalCode": "...",
     *   "report": "..."
     * }
     *
     * HTTP STATUS CODES:
     * - 200 OK: Review completed (even if code couldn't be fixed and will ask to manually fix
     *           based on history/ report and then send it back to the same endpoint)
     * - 400 Bad Request: Invalid input
     * - 500 Internal Server Error: System error
     */
    @PostMapping("/review")
    public ResponseEntity<CodeReviewResponse> reviewCode(
            @RequestBody CodeReviewRequest request
    ) {
        System.out.println("\n Received code review request");
        System.out.println("Language: " + request.getLanguage());
        System.out.println("Code length: " +
                (request.getCode() != null ? request.getCode().length() : 0) + " chars");

        try {
            // Validate request
            validateRequest(request);

            // Process the review
            CodeReviewResponse response = orchestrator.reviewCode(request);

            // Return success response
            System.out.println(" Review complete. Success: " + response.isSuccess());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Bad request - invalid input
            System.err.println(" Invalid request: " + e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(createErrorResponse(e.getMessage()));

        } catch (Exception e) {
            // Internal error
            System.err.println(" Internal error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Validate incoming request
     *
     * VALIDATION RULES:
     * - Code must not be null or empty
     * - Language must be "python" or "javascript" (as of now )
     * - Code must not exceed reasonable length (prevent abuse)
     */
    private void validateRequest(CodeReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Code is required");
        }

        if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
            throw new IllegalArgumentException("Language is required");
        }

        String language = request.getLanguage().toLowerCase();
        if (!language.equals("python") && !language.equals("javascript") && !language.equals("js")) {
            throw new IllegalArgumentException(
                    "Unsupported language: " + request.getLanguage() +
                            ". Supported: python, javascript"
            );
        }


        if (request.getCode().length() > 10_000) {
            throw new IllegalArgumentException(
                    "Code too large. Maximum 10,000 characters allowed"
            );
        }
    }

    /**
     * Create error response
     */
    private CodeReviewResponse createErrorResponse(String errorMessage) {
        CodeReviewResponse response = new CodeReviewResponse();
        response.setSuccess(false);
        response.setReport("Error: " + errorMessage);
        return response;
    }

    /**
     * Health check endpoint
     *
     * USAGE: Check if service is running
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
                "ok",
                "Code Review Agent is running",
                System.currentTimeMillis()
        ));
    }

    /**
     * Simple health response DTO
     */
    public static class HealthResponse {
        private String status;
        private String message;
        private long timestamp;

        public HealthResponse(String status, String message, long timestamp) {
            this.status = status;
            this.message = message;
            this.timestamp = timestamp;
        }

        // Getters
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }
}
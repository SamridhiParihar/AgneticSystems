package com.codereview.orchestrator;

import com.codereview.model.*;
import com.codereview.repository.StateRepository;
import com.codereview.service.LLMService;
import com.codereview.tool.CodeExecutionTool;
import org.springframework.stereotype.Component;

/**
 * The Orchestrator - coordinates the entire code review process
 *
 * ARCHITECTURE PATTERN: This is a "Coordinator" or "Saga" pattern
 * - Manages complex multi-step workflow
 * - Handles state transitions
 *
 * THE REFLECTION LOOP:
 * 1. Planning: Analyze code → identify issues → create fix plan
 * 2. Execution Loop (max 3 attempts):
 *    a. Generate fix based on plan + history
 *    b. Execute code in Docker ( sandbox env)
 *    c. Check result:
 *       - Success? → Done, generate report
 *       - Failure? → Reflect on what went wrong → Try again
 * 3. Final Report: Summarize what happened
 *
 * WHY ORCHESTRATOR PATTERN:
 * - Single source of truth for workflow logic
 * - Easy to modify the loop without touching services
 * - Clear separation: Orchestrator = "what to do", Services = "how to do it"
 */
@Component
public class CodeReviewOrchestrator {

    private final LLMService llmService;
    private final CodeExecutionTool executionTool;
    private final StateRepository stateRepository;

    public CodeReviewOrchestrator(
            LLMService llmService,
            CodeExecutionTool executionTool,
            StateRepository stateRepository
    ) {
        this.llmService = llmService;
        this.executionTool = executionTool;
        this.stateRepository = stateRepository;
    }

    /**
     * Main entry point: Review and fix code
     *
     * WORKFLOW:
     * User submits code → We analyze it → Try to fix it → Return results
     *
     * @param request Contains code + language
     * @return Complete review with all attempts and final result
     */
    public CodeReviewResponse reviewCode(CodeReviewRequest request) {
        System.out.println("\n========================================");
        System.out.println("STARTING CODE REVIEW");
        System.out.println("Language: " + request.getLanguage());
        System.out.println("========================================\n");

        // Step 1: Initialize state
        CodeReviewState state = new CodeReviewState(
                request.getCode(),
                request.getLanguage()
        );
        stateRepository.save(state);

        try {
            // Step 2: Planning Phase
            planningPhase(state);

            // Step 3: Execution Loop (max 3 attempts)
            executionLoop(state);

            // Step 4: Generate Final Report
            generateFinalReport(state);

            // Step 5: Save final state and return response
            stateRepository.save(state);
            return buildResponse(state);

        } catch (Exception e) {
            System.err.println("Code review failed: " + e.getMessage());
            e.printStackTrace();

            // Save error state
            state.setFinalReport("Review failed: " + e.getMessage());
            stateRepository.save(state);

            return buildErrorResponse(state, e.getMessage());
        }
    }

    /**
     * PHASE 1: PLANNING
     *
     * Use LLM to:
     * - Analyze the code
     * - Identify all issues
     * - Create a fix strategy
     *
     * WHY SEPARATE PHASE:
     * - Clear separation of analysis vs execution
     * - Plan once, execute multiple times
     * - LLM can see the big picture before diving into fixes
     */
    private void planningPhase(CodeReviewState state) {
        System.out.println("\nPHASE 1: PLANNING");
        System.out.println("-------------------");

        llmService.analyzeAndPlan(state);

        System.out.println("Planning complete");
        System.out.println("Issues found: " + state.getIdentifiedIssues().size());

        for (Issue issue : state.getIdentifiedIssues()) {
            System.out.println(String.format(
                    "  - Line %d [%s]: %s",
                    issue.getLineNumber(),
                    issue.getSeverity(),
                    issue.getDescription()
            ));
        }

        System.out.println("\nFix Plan: " + state.getFixPlan());
        stateRepository.save(state);
    }

    /**
     * PHASE 2: EXECUTION LOOP
     *
     * THE HEART OF THE AGENT:
     * This is where reflection happens!
     *
     * Loop logic:
     * while (not fixed AND attempts < 3):
     *   1. Generate reasoning for this attempt
     *   2. Generate fixed code based on reasoning
     *   3. Execute code in Docker
     *   4. Check results:
     *      - If success → Mark as fixed, exit loop
     *      - If failure → Reflect on why, continue loop
     *
     * WHY MAX 3 ATTEMPTS:
     * - Prevents infinite loops
     * - Forces the agent to succeed quickly
     * - 3 is usually enough for simple fixes
     * - Can be increased if needed
     */
    private void executionLoop(CodeReviewState state) {
        System.out.println("\nPHASE 2: EXECUTION LOOP");
        System.out.println("-------------------------");

        int attemptNumber = 1;

        while (state.shouldContinue()) {
            System.out.println(String.format(
                    "\n🔧 ATTEMPT %d/%d",
                    attemptNumber,
                    state.getMaxAttempts()
            ));
            System.out.println("-------------");

            try {
                // Step 2a: Generate reasoning for this attempt
                String reasoning = generateReasoning(state, attemptNumber);
                System.out.println("Reasoning: " + reasoning);

                // Step 2b: Generate fixed code
                String fixedCode = llmService.generateFixedCode(state, reasoning);
                System.out.println("Generated fixed code (" + fixedCode.length() + " chars)");

                // Step 2c: Create attempt record
                Attempt attempt = new Attempt(attemptNumber, fixedCode, reasoning);

                // Step 2d: Execute in Docker
                System.out.println("Executing in Docker...");
                ExecutionResult result = executionTool.executeCode(
                        fixedCode,
                        state.getLanguage()
                );
                attempt.setExecutionResult(result);

                // Log execution results
                logExecutionResult(result);

                // Step 2e: Check if fixed
                if (result.isSuccess()) {
                    System.out.println("SUCCESS! Code is fixed.");
                    state.setFixed(true);
                    state.setFinalCode(fixedCode);
                    attempt.setReflection("Success - code executes without errors");

                } else {
                    System.out.println("Still failing. Reflecting...");

                    // Step 2f: Reflect on failure
                    state.addAttempt(attempt); // Add before reflection so LLM can see it
                    String reflection = llmService.reflect(state);
                    attempt.setReflection(reflection);

                    System.out.println("Reflection: " + reflection);
                }

                // Step 2g: Save attempt to state
                if (!state.getAttempts().contains(attempt)) {
                    state.addAttempt(attempt);
                }
                stateRepository.save(state);

                attemptNumber++;

            } catch (Exception e) {
                System.err.println("Attempt failed with exception: " + e.getMessage());

                // Create failed attempt record
                Attempt failedAttempt = new Attempt(attemptNumber, "", "Execution failed");
                ExecutionResult errorResult = new ExecutionResult();
                errorResult.setSuccess(false);
                errorResult.setError(e.getMessage());
                failedAttempt.setExecutionResult(errorResult);
                failedAttempt.setReflection("Execution crashed: " + e.getMessage());

                state.addAttempt(failedAttempt);
                stateRepository.save(state);

                attemptNumber++;
            }
        }

        // Loop ended - check why
        if (state.isFixed()) {
            System.out.println("\n Code successfully fixed in " + state.getAttempts().size() + " attempt(s)");
        } else {
            System.out.println("\n Max attempts reached. Code still has issues.");
            state.setFinalCode(state.getOriginalCode()); // Fall back to original
        }
    }

    /**
     * Generate reasoning for the current attempt
     *
     * REASONING STRATEGY:
     * - Attempt 1: Focus on fixing identified issues
     * - Attempt 2+: Learn from previous failures
     *
     * WHY THIS MATTERS:
     * - Gives the LLM clear direction
     * - Prevents repeating the same mistake
     * - Each attempt builds on previous learnings
     */
    private String generateReasoning(CodeReviewState state, int attemptNumber) {
        if (attemptNumber == 1) {
            // First attempt: Focus on the fix plan
            return "First attempt: " + state.getFixPlan();
        } else {
            // Subsequent attempts: Learn from previous failures
            Attempt lastAttempt = state.getLastAttempt();
            if (lastAttempt != null && lastAttempt.getReflection() != null) {
                return String.format(
                        "Attempt %d: Based on previous failure, %s",
                        attemptNumber,
                        lastAttempt.getReflection()
                );
            } else {
                return String.format(
                        "Attempt %d: Previous approach failed, trying alternative fix",
                        attemptNumber
                );
            }
        }
    }

    /**
     * PHASE 3: GENERATE FINAL REPORT
     *
     * Summarize everything that happened:
     * - What issues were found
     * - How many attempts it took
     * - Whether we succeeded
     * - What the final code looks like
     */
    private void generateFinalReport(CodeReviewState state) {
        System.out.println("\n PHASE 3: GENERATING REPORT");
        System.out.println("----------------------------");

        StringBuilder report = new StringBuilder();

        report.append("CODE REVIEW REPORT\n");
        report.append("==================\n\n");

        // Section 1: Issues Found
        report.append("ISSUES IDENTIFIED:\n");
        if (state.getIdentifiedIssues().isEmpty()) {
            report.append("  No issues found.\n");
        } else {
            for (Issue issue : state.getIdentifiedIssues()) {
                report.append(String.format(
                        "  - Line %d [%s/%s]: %s\n",
                        issue.getLineNumber(),
                        issue.getSeverity(),
                        issue.getCategory(),
                        issue.getDescription()
                ));
            }
        }
        report.append("\n");

        // Section 2: Fix Strategy
        report.append("FIX STRATEGY:\n");
        report.append("  " + state.getFixPlan() + "\n\n");

        // Section 3: Execution Summary
        report.append("EXECUTION SUMMARY:\n");
        report.append(String.format(
                "  Total attempts: %d\n",
                state.getAttempts().size()
        ));

        for (Attempt attempt : state.getAttempts()) {
            report.append(String.format(
                    "\n  Attempt %d:\n",
                    attempt.getAttemptNumber()
            ));
            report.append(String.format(
                    "    Reasoning: %s\n",
                    attempt.getReasoning()
            ));
            report.append(String.format(
                    "    Result: %s\n",
                    attempt.getExecutionResult().isSuccess() ? "SUCCESS" : "FAILED"
            ));

            if (!attempt.getExecutionResult().isSuccess()) {
                report.append(String.format(
                        "    Error: %s\n",
                        truncate(attempt.getExecutionResult().getStderr(), 200)
                ));
            }

            if (attempt.getReflection() != null) {
                report.append(String.format(
                        "    Reflection: %s\n",
                        attempt.getReflection()
                ));
            }
        }
        report.append("\n");

        // Section 4: Final Status
        report.append("FINAL STATUS:\n");
        if (state.isFixed()) {
            report.append("  Code successfully fixed and executes without errors.\n");
        } else {
            report.append("  Code still has issues after maximum attempts.\n");
            report.append("  Consider:\n");
            report.append("    - Reviewing the error messages above\n");
            report.append("    - Manually fixing critical issues\n");
            report.append("    - Trying again with modified code\n");
        }

        state.setFinalReport(report.toString());
        System.out.println(" Report generated");
    }

    /**
     * Build successful response
     */
    private CodeReviewResponse buildResponse(CodeReviewState state) {
        CodeReviewResponse response = new CodeReviewResponse();
        response.setReviewId(state.getId());
        response.setIssuesFound(state.getIdentifiedIssues());
        response.setFixPlan(state.getFixPlan());
        response.setAttempts(state.getAttempts());
        response.setSuccess(state.isFixed());
        response.setFinalCode(state.getFinalCode());
        response.setReport(state.getFinalReport());
        return response;
    }

    /**
     * Build error response
     */
    private CodeReviewResponse buildErrorResponse(CodeReviewState state, String error) {
        CodeReviewResponse response = new CodeReviewResponse();
        response.setReviewId(state.getId());
        response.setSuccess(false);
        response.setReport("Review failed: " + error);
        return response;
    }

    /**
     * Helper: Log execution result
     */
    private void logExecutionResult(ExecutionResult result) {
        if (result.isSuccess()) {
            System.out.println("Exit Code: " + result.getExitCode());
            if (!result.getStdout().isEmpty()) {
                System.out.println("Output:\n" + result.getStdout());
            }
        } else {
            System.out.println("Exit Code: " + result.getExitCode());
            if (!result.getStderr().isEmpty()) {
                System.out.println("Error:\n" + truncate(result.getStderr(), 500));
            }
            if (result.getError() != null) {
                System.out.println("Execution Error: " + result.getError());
            }
        }
    }

    /**
     * Helper: Truncate long strings
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "... (truncated)";
    }
}
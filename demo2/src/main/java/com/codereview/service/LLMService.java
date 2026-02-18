package com.codereview.service;

import com.codereview.model.CodeReviewState;
import com.codereview.model.Issue;
import java.util.List;

/**
 * Interface for LLM operations
 *
 * WHY AN INTERFACE?
 * - Easy to swap implementations (Groq, OpenAI, local model)
 * - Makes testing easier (can mock this)
 * - Clean separation of concerns
 *
 * THREE CORE OPERATIONS matching our agent design:
 * 1. analyzeAndPlan() - PLANNING AGENT
 * 2. generateFixedCode() - EXECUTOR AGENT
 * 3. reflect() - REFLECTION AGENT ( with external feedback )
 */
public interface LLMService {

    /**
     * PLANNING AGENT: Analyze code and create fix plan
     *
     * INPUT: Original buggy code + language
     * OUTPUT: List of issues found + strategy to fix them
     *
     * WHY: We Need to understand what's wrong before fixing
     */
    void analyzeAndPlan(CodeReviewState state);

    /**
     * EXECUTOR AGENT: Generate fixed code based on plan
     *
     * INPUT: State with issues + fix plan + (optional) previous attempts if not first time
     * OUTPUT: Modified code attempting to fix the issues
     *
     * WHY: Take the plan and actually write the fixed code
     */
    String generateFixedCode(CodeReviewState state, String reasoning);

    /**
     * REFLECTION AGENT: Learn from execution results
     *
     * INPUT: What we tried + execution result (success/failure) ( kind of external feedback )
     * OUTPUT: Analysis of what went wrong + what to try next
     *
     * WHY: If code still fails, figure out why and adjust strategy
     */
    String reflect(CodeReviewState state);
}
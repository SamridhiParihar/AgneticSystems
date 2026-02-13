package com.task_breakdown_assistant.demo.llm;

import com.task_breakdown_assistant.demo.model.StepResponse;

public interface LLLMProvider {
    StepResponse generateSteps(String prompt);
}

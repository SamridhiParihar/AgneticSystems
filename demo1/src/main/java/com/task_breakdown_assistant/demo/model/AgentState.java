package com.task_breakdown_assistant.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Accepted model for the agent state
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    private String id;
    private String goal;
    private List<Step> steps;
    private int currentStepIndex;
    private List<String> thoughts;
    private boolean isComplete;

    public static AgentState from(AgentState oldState) {
        return AgentState.builder()
                .id(oldState.getId())
                .goal(oldState.getGoal())
                .steps(oldState.getSteps() != null ? new ArrayList<>(oldState.getSteps()) : new ArrayList<>())
                .currentStepIndex(oldState.getCurrentStepIndex())
                .thoughts(oldState.getThoughts() != null ? new ArrayList<>(oldState.getThoughts()) : new ArrayList<>())
                .isComplete(oldState.isComplete())
                .build();
    }

    public void addThought(String thought) {
        if (thoughts == null) {
            thoughts = new ArrayList<>();
        }
        thoughts.add(thought);
    }

    public void completeStep(String result) {
        if (steps != null && currentStepIndex < steps.size()) {
            Step currentStep = steps.get(currentStepIndex);
            currentStep.setStatus(Step.StepStatus.COMPLETED);
            currentStep.setResult(result);
            currentStepIndex++;

            if (currentStepIndex >= steps.size()) {
                isComplete = true;
            }
        }
    }

    public Step getCurrentStep() {
        if (steps != null && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }
}


package com.task_breakdown_assistant.demo.service;

import com.task_breakdown_assistant.demo.llm.LLMClient;
import com.task_breakdown_assistant.demo.model.*;
import com.task_breakdown_assistant.demo.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TaskBreakDownService {

    private final LLMClient llmClient ;
    private final AgentRepository agentRepository;

    public Response startAgent(Request request) {
        StepResponse stepResponse = llmClient.getStepResponse(request.getInput());
        
        // Convert List<String> steps to List<Step>
        List<Step> steps = stepResponse.getSteps().stream()
                .map(step -> new Step(step.getDescription(), Step.StepStatus.PENDING, null, null))
                .collect(Collectors.toList());

        AgentState state = AgentState.builder()
                .id(UUID.randomUUID().toString())
                .goal(request.getInput())
                .steps(steps)
                .currentStepIndex(0)
                .thoughts(new ArrayList<>())
                .isComplete(false)
                .build();

        agentRepository.save(state);

        // Update the response to include thoughts
        StepResponse response = new StepResponse(steps, state.getThoughts());
        return new Response(state.getId(), response);
    }

    public Optional<ExecutorResponse> executeNext(String agentId) {
        return agentRepository.find(agentId)
                .map(state -> {
                    if (state.isComplete()) {
                        return new ExecutorResponse("All steps completed", "", "", true, "");
                    }

                    Step currentStep = state.getCurrentStep();
                    if (currentStep == null) {
                        return new ExecutorResponse("No more steps", "", "", true, "");
                    }

                    // Generate thought for the current step
                    String thought = generateThought(state);
                    state.addThought(thought);
                    currentStep.setThought(thought);
                    currentStep.setStatus(Step.StepStatus.RUNNING);

                    // Execute the step
                    String result = llmClient.explainStep(currentStep.getDescription());
                    currentStep.setResult(result);
                    currentStep.setStatus(Step.StepStatus.COMPLETED);
                    state.completeStep(result);

                    // Save the updated state
                    agentRepository.save(state);

                    // Determine next step description
                    String nextStep = state.getCurrentStepIndex() < state.getSteps().size() ? 
                            state.getCurrentStep().getDescription() : "";

                    return new ExecutorResponse(
                            currentStep.getDescription(),
                            result,
                            thought,
                            state.isComplete(),
                            nextStep
                    );
                });
    }

    public Flux<ExecutorResponse> runAutonomously(String agentId) {
        return Flux.generate(() -> agentRepository.find(agentId).orElseThrow(),
                (state, sink) -> {
                    if (state.isComplete()) {
                        sink.complete();
                        return state;
                    }

                    // Generate and save thought
                    String thought = generateThought(state);
                    state.addThought(thought);
                    
                    // Get current step and update its status
                    Step currentStep = state.getCurrentStep();
                    currentStep.setThought(thought);
                    currentStep.setStatus(Step.StepStatus.RUNNING);
                    
                    // Execute the step
                    String result = llmClient.explainStep(currentStep.getDescription());
                    currentStep.setResult(result);
                    currentStep.setStatus(Step.StepStatus.COMPLETED);
                    state.completeStep(result);
                    
                    // Save the updated state
                    agentRepository.save(state);
                    
                    // Determine next step description
                    String nextStep = state.getCurrentStepIndex() < state.getSteps().size() ? 
                            state.getCurrentStep().getDescription() : "";
                    
                    // Emit the response
                    sink.next(new ExecutorResponse(
                            currentStep.getDescription(),
                            result,
                            thought,
                            state.isComplete(),
                            nextStep
                    ));
                    
                    if (state.isComplete()) {
                        sink.complete();
                    }
                    
                    return state;
                },
                state -> {}
        );
    }
    
    private String generateThought(AgentState state) {
        // a context for the LLM to generate a thought
        StringBuilder context = new StringBuilder();
        context.append("Goal: ").append(state.getGoal()).append("\n\n");
        context.append("Completed steps:\n");
        
        for (int i = 0; i < state.getCurrentStepIndex(); i++) {
            Step step = state.getSteps().get(i);
            context.append("- ").append(step.getDescription())
                   .append("\n  Result: ").append(step.getResult())
                   .append("\n\n");
        }
        
        if (state.getCurrentStepIndex() < state.getSteps().size()) {
            context.append("Next step to execute: ")
                  .append(state.getCurrentStep().getDescription())
                  .append("\n\n");
        }
        
        context.append("What should be the thought process for this step?");
        
        // Use the LLM to generate a thought
        return llmClient.generateThought(context.toString());
    }

}




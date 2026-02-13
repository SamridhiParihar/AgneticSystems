package com.task_breakdown_assistant.demo.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task_breakdown_assistant.demo.model.Step;
import com.task_breakdown_assistant.demo.model.StepResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LLMClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final WebClient webClient;

    public LLMClient(WebClient.Builder webClientBuilder, @Value("${open-ai.api-key}") String apiKey) {
        System.out.println("Api key : "+ apiKey);
        this.webClient = webClientBuilder
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public StepResponse getStepResponse(String goal) {
        try {
            System.out.println("Received goal: " + goal);
            
            String prompt = """
                Break the following goal into exactly  actionable steps.
                Return ONLY JSON in this format , should have strict contract as below :

                {
                  "steps": ["step1", "step2", "step3", "step4", "step5"]
                }

                Goal: %s
                """.formatted(goal);

            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            System.out.println("Sending request to OpenAI API...");
            
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                System.err.println("Error response status: " + clientResponse.statusCode());
                                return clientResponse.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            System.err.println("Error response body: " + body);
                                            return Mono.error(new RuntimeException("API Error: " + body));
                                        });
                            })
                    .bodyToMono(String.class)
                    .block();

            System.out.println("Received response from OpenAI API");
            System.out.println("response is : "+ response);
            return parseResponse(response);
            
        } catch (Exception e) {
            System.err.println("Error in getStepResponse: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private StepResponse parseResponse(String apiResponse) {
        if (apiResponse == null || apiResponse.isBlank()) {
            throw new RuntimeException("Empty response from API");
        }

        try {
            JsonNode root = mapper.readTree(apiResponse);
            
            // Extract the content string which contains the actual JSON
            String content = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("LLM returned empty content");
            }

            // Parse the content string to get the steps array
            JsonNode contentNode = mapper.readTree(content);
            JsonNode stepsArray = contentNode.path("steps");
            
            // Convert the string steps to Step objects
            List<Step> steps = new ArrayList<>();
            for (JsonNode stepNode : stepsArray) {
                Step step = new Step();
                step.setDescription(stepNode.asText());
                step.setStatus(Step.StepStatus.PENDING);
                steps.add(step);
            }
            
            return new StepResponse(steps, new ArrayList<>());

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }


    public String explainStep(String step) {
        try {
            String prompt = """
                Explain how to complete the following step in detail:
                %s
                
                Provide a clear, step-by-step explanation.
                """.formatted(step);

            return callLLM(prompt, 0.7);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating explanation: " + e.getMessage();
        }
    }
    
    public String generateThought(String context) {
        try {
            String prompt = """
                Based on the following context, provide a brief thought process for the next step.
                Be concise and focus on the key considerations.
                
                Context:
                %s
                
                Thought process:
                """.formatted(context);
                
            return callLLM(prompt, 0.8);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating thought: " + e.getMessage();
        }
    }
    
    private String callLLM(String prompt, double temperature) {
        Map<String, Object> requestBody = Map.of(
            "model", "llama-3.3-70b-versatile",
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", temperature
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response.path("choices").get(0).path("message").path("content").asText();
    }



}

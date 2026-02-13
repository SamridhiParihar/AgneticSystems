package com.task_breakdown_assistant.demo.repository;

import com.task_breakdown_assistant.demo.model.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for managing AgentState instances in-memory.
 * Note: This implementation uses a simple HashMap and is not persistent across application restarts.
 * For production use, consider implementing a proper database-backed repository.
 */
@Component
public class AgentRepository {

    private final Map<String, AgentState> store = new HashMap<>();

    /**
     * Saves the given AgentState to the repository.
     * @param state The AgentState to save
     * @throws IllegalArgumentException if state or state.getId() is null
     */
    public void save(AgentState state) {
        if (state == null || state.getId() == null) {
            throw new IllegalArgumentException("AgentState and its ID must not be null");
        }
        store.put(state.getId(), state);
    }

    /**
     * Finds an AgentState by its ID.
     * @param id The ID of the AgentState to find
     * @return An Optional containing the found AgentState, or empty if not found
     */
    public Optional<AgentState> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
}


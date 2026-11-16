package io.github.assafep.jobstein.provisioning;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class ProvisioningStateRepository {
    public enum Status { IN_PROGRESS, COMPLETED, FAILED }
    public record Checkpoint(Status status, String updatedAt) {
        public Checkpoint { Objects.requireNonNull(status); Instant.parse(updatedAt); }
    }
    public record State(int schemaVersion, Map<String, Checkpoint> steps) {
        public State {
            if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported state schema");
            steps = Map.copyOf(steps);
        }
    }

    private final ConfigurationRepository repository;
    public ProvisioningStateRepository(ConfigurationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    public State load() throws IOException {
        return repository.load("provisioning-state", State.class).orElseGet(() -> new State(1, Map.of()));
    }
    public synchronized void record(String stepId, Status status) throws IOException {
        if (stepId == null || !stepId.matches("[a-z][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("Invalid step ID");
        var steps = new HashMap<>(load().steps());
        steps.put(stepId, new Checkpoint(status, Instant.now().toString()));
        repository.save("provisioning-state", new State(1, steps));
    }
    public boolean completed(String stepId) throws IOException {
        return Optional.ofNullable(load().steps().get(stepId))
            .map(Checkpoint::status).filter(status -> status == Status.COMPLETED).isPresent();
    }
}

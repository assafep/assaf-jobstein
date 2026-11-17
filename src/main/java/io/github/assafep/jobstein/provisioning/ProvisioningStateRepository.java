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
    public record FirstProfile(String role,String slug,UUID id) {
        public FirstProfile {
            if(role==null||role.isBlank()||slug==null||!slug.equals(io.github.assafep.jobstein.storage.UserProfileService.slug(role)))
                throw new IllegalArgumentException("Invalid first profile selection");
            Objects.requireNonNull(id);
        }
    }
    public Optional<FirstProfile> firstProfile()throws IOException {
        return repository.load("onboarding-first-profile",FirstProfile.class);
    }
    public synchronized void selectFirstProfile(FirstProfile selection)throws IOException {
        var existing=firstProfile();
        if(existing.isPresent()&&!existing.get().equals(selection))throw new IOException("First profile selection is already bound");
        if(existing.isEmpty())repository.save("onboarding-first-profile",selection);
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

package io.github.assafep.jobstein.provisioning;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.validation.DiagnosticException;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class ProvisioningStateRepository {
    private static final Set<String> CHECKPOINTS = Set.of(
        "google-configure", "google-authorize", "ai-configure", "user-initialize", "first-profile");
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

    /** Validates local onboarding metadata without contacting or mutating remote services. */
    public void validate() throws IOException {
        final State current;
        final Optional<FirstProfile> selected;
        try {
            current = load();
            selected = firstProfile();
        } catch (RuntimeException invalid) {
            throw new DiagnosticException(DiagnosticException.Code.ONBOARDING_CHECKPOINT_INVALID, invalid);
        }
        if (current.steps().keySet().stream().anyMatch(id -> !CHECKPOINTS.contains(id))) {
            throw new DiagnosticException(DiagnosticException.Code.ONBOARDING_CHECKPOINT_INVALID);
        }
        boolean firstProfileCompleted = Optional.ofNullable(current.steps().get("first-profile"))
            .map(Checkpoint::status).filter(Status.COMPLETED::equals).isPresent();
        if (firstProfileCompleted && selected.isEmpty()) {
            throw new DiagnosticException(DiagnosticException.Code.ONBOARDING_CHECKPOINT_INVALID);
        }
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

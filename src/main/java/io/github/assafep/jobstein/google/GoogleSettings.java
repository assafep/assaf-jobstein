package io.github.assafep.jobstein.google;
public record GoogleSettings(String projectId) {
    public GoogleSettings {
        if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]"))
            throw new IllegalArgumentException("Invalid GCP project ID");
    }
}

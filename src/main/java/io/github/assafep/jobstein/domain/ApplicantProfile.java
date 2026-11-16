package io.github.assafep.jobstein.domain;

import java.util.UUID;
import java.util.Objects;

public record ApplicantProfile(UUID id, UUID userId, String targetRole, String slug, Status status) {
    public enum Status { INITIALIZING, RESUME_UPLOADED, DESCRIPTOR_GENERATING, AWAITING_REVIEW, ACTIVE, INACTIVE }
    public ApplicantProfile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(status);
        if (targetRole == null || targetRole.isBlank() || slug == null
            || !slug.matches("[a-z0-9]+([.-][a-z0-9]+)*") || slug.equals("shared"))
            throw new IllegalArgumentException("Invalid applicant profile");
    }
}

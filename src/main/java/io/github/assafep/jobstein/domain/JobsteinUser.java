package io.github.assafep.jobstein.domain;

import java.util.UUID;
import java.util.Objects;

public record JobsteinUser(UUID id, String fullName, String slug, String googleAccountSubject) {
    public JobsteinUser {
        Objects.requireNonNull(id);
        if (fullName == null || fullName.isBlank() || googleAccountSubject == null || googleAccountSubject.isBlank()
            || slug == null || !slug.matches("[a-z0-9]+([.-][a-z0-9]+)*"))
            throw new IllegalArgumentException("Invalid user identity");
    }
}

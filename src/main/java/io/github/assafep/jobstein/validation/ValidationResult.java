package io.github.assafep.jobstein.validation;

import java.util.List;
import java.util.Objects;

public record ValidationResult(List<Check> checks) {
    public enum Status { PASS, WARNING, FAIL, NOT_CHECKED }
    public record Check(String id, Status status, String message) {
        public Check { Objects.requireNonNull(id); Objects.requireNonNull(status); Objects.requireNonNull(message); }
    }
    public ValidationResult { checks = List.copyOf(checks); }
    public boolean ready() {
        return !checks.isEmpty() && checks.stream().allMatch(c -> c.status() == Status.PASS || c.status() == Status.WARNING);
    }
}

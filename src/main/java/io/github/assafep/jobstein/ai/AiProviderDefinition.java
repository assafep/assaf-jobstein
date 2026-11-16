package io.github.assafep.jobstein.ai;

import java.util.*;

public record AiProviderDefinition(String id, String displayName, String adapterType,
    Map<String, Field> fields, Set<String> capabilities) {
    public enum FieldType { STRING, SECRET, ENUM, INTEGER, DECIMAL, BOOLEAN, URL }
    public record Field(FieldType type, boolean required, String displayName,
        Optional<String> defaultValue, List<String> options) {
        public Field {
            Objects.requireNonNull(type);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(defaultValue);
            options = List.copyOf(options);
            if (type == FieldType.SECRET && defaultValue.isPresent())
                throw new IllegalArgumentException("Secret defaults are forbidden");
        }
    }
    public AiProviderDefinition {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("Invalid provider ID");
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(adapterType);
        fields = Map.copyOf(fields);
        capabilities = Set.copyOf(capabilities);
    }
}

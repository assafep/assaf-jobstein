package io.github.assafep.jobstein.security;

import java.io.IOException;
import java.util.Optional;

public interface SecretStore {
    void put(String reference, byte[] value) throws IOException;
    Optional<byte[]> get(String reference) throws IOException;
    void remove(String reference) throws IOException;
}

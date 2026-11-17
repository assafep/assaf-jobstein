package io.github.assafep.jobstein.security;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class CredentialStores {
    private CredentialStores(){}
    public enum Backend { FILE, SECRET_SERVICE }
    public record Settings(int schemaVersion,Backend backend){
        public Settings{
            if(schemaVersion!=1)throw new IllegalArgumentException("Unsupported credential settings");
            Objects.requireNonNull(backend);
        }
    }
    public static SecretStore open(Path directory,ConfigurationRepository config,Optional<String> requested)throws IOException{
        var existing=config.load("credential-store",Settings.class);
        Optional<Backend> selection=requested.map(value->switch(value){
            case "file" -> Backend.FILE;
            case "secret-service" -> Backend.SECRET_SERVICE;
            default -> throw new IllegalArgumentException("Unknown credential backend");
        });
        Backend backend=existing.map(Settings::backend).orElseGet(()->selection.orElse(Backend.FILE));
        if(selection.isPresent()&&selection.get()!=backend)throw new IOException("Credential backend is already fixed");
        // Never implicitly abandon existing file credentials.
        if(backend==Backend.SECRET_SERVICE&&Files.exists(directory.resolve("secrets"),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Existing file credentials require explicit migration");
        SecretStore store=switch(backend){
            case FILE -> new PosixFileSecretStore(directory.resolve("secrets"));
            case SECRET_SERVICE -> throw new IOException("Native credential backend is experimental; use file until missing-entry handling is verified");
        };
        if(existing.isEmpty())config.save("credential-store",new Settings(1,backend));
        return store;
    }
}

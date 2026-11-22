package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.environment.EnvironmentService;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.google.GoogleSettings;
import io.github.assafep.jobstein.security.CredentialStores;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import picocli.CommandLine;
import java.util.concurrent.Callable;

@CommandLine.Command(mixinStandardHelpOptions = true)
final class EnvironmentCommand implements Callable<Integer> {
    private final EnvironmentService service;
    private final String operation;
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    @CommandLine.Option(names = "--env", defaultValue = "production")
    String environment;

    EnvironmentCommand(EnvironmentService service, String operation) {
        this.service = service;
        this.operation = operation;
    }

    public Integer call() {
        try {
            var out = spec.commandLine().getOut();
            switch (operation) {
                case "create" -> {
                    try(var ignored=io.github.assafep.jobstein.environment.EnvironmentLock.acquire(service.directory(environment))){out.println(service.create(environment));}
                }
                case "list" -> service.list().forEach(out::println);
                case "show" -> out.println(java.nio.file.Files.readString(service.validate(environment)));
                case "validate" -> {
                    service.validate(environment);
                    out.println("Local environment metadata valid: " + environment);
                }
                case "current" -> {
                    service.validate(environment);
                    var google = new ConfigurationRepository(service.directory(environment)).load("google", GoogleSettings.class);
                    var config = new ConfigurationRepository(service.directory(environment));
                    out.println("Environment: " + environment);
                    out.println("Directory: " + service.directory(environment));
                    out.println("Type: " + type(environment));
                    out.println("GCP project: " + google.map(GoogleSettings::projectId).orElse("(not configured)"));
                    out.println("Google OAuth credentials: " + (oauthConfigured(service.directory(environment), config) ? "configured" : "not configured"));
                }
                default -> throw new IllegalStateException("Unknown environment operation");
            }
            return 0;
        } catch (io.github.assafep.jobstein.environment.EnvironmentLock.BusyException e) {
            spec.commandLine().getErr().println(e.getMessage());return 4;
        } catch (java.io.IOException | IllegalArgumentException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return 1;
        }
    }

    private static String type(String name) {
        if ("sandbox".equals(name)) return "sandbox";
        if ("production".equals(name)) return "production";
        return "live";
    }

    private static boolean oauthConfigured(java.nio.file.Path directory, ConfigurationRepository config) throws java.io.IOException {
        var settings = config.load("credential-store", CredentialStores.Settings.class);
        if (settings.isPresent() && settings.get().backend() != CredentialStores.Backend.FILE) return false;
        var store = new PosixFileSecretStore(directory.resolve("secrets"));
        return store.get("secret://google/client").isPresent() && store.get("secret://google/refresh").isPresent();
    }
}

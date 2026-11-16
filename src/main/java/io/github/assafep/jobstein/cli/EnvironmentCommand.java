package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.environment.EnvironmentService;
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
}

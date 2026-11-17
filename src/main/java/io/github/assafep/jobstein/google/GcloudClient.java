package io.github.assafep.jobstein.google;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.github.assafep.jobstein.validation.DiagnosticException;

public final class GcloudClient {
    public static final List<String> APIS = List.of("drive.googleapis.com","docs.googleapis.com","gmail.googleapis.com","calendar-json.googleapis.com");
    public String execute(String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("gcloud");
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile("jobstein-gcloud-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DiagnosticException(DiagnosticException.Code.GCP_TIMEOUT);
            }
            if (process.exitValue() != 0) throw new DiagnosticException(DiagnosticException.Code.GCP_COMMAND_FAILED);
            return Files.readString(output).trim();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }
    public void validateProject(String project) throws IOException, InterruptedException {
        if (project == null || !project.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]"))
            throw new IllegalArgumentException("Invalid GCP project ID");
    }
    public void enableApis(String project) throws IOException, InterruptedException {
        validateProject(project);
        var args = new ArrayList<>(List.of("services","enable"));
        args.addAll(APIS);
        args.addAll(List.of("--project="+project,"--quiet"));
        execute(args.toArray(String[]::new));
    }
    public void verify(String project) throws IOException, InterruptedException {
        validateProject(project);
        execute("projects","describe",project,"--format=value(projectId)");
        var enabled = Set.copyOf(execute("services","list","--enabled","--project="+project,"--format=value(config.name)").lines().toList());
        if (!enabled.containsAll(APIS)) throw new DiagnosticException(DiagnosticException.Code.GCP_APIS_MISSING);
    }
    public void configure(String project, boolean create) throws IOException, InterruptedException {
        validateProject(project);
        if (create) {
            try { execute("projects","describe",project,"--format=value(projectId)"); }
            catch (IOException e) { execute("projects","create",project,"--quiet"); }
        }
        enableApis(project);
        verify(project);
    }
}

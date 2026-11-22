package io.github.assafep.jobstein.cli;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.environment.EnvironmentService;
import io.github.assafep.jobstein.google.GoogleSettings;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JobsteinTest {
    @TempDir Path root;
    @Test void exposesAdministrativeCommands() {
        var cli = Jobstein.commandLine();
        assertTrue(cli.getSubcommands().keySet().containsAll(
            java.util.List.of("onboard", "doctor", "environment", "google", "ai", "user", "profile")));
        assertFalse(cli.getSubcommands().containsKey("start"));
        assertTrue(cli.getSubcommands().get("profile").getSubcommands().containsKey("descriptor"));
    }

    @Test void nonInteractiveInvalidEnvironmentFails() {
        assertNotEquals(0, Jobstein.commandLine().execute("doctor", "--env", "../bad", "--non-interactive"));
    }

    @Test void helpSucceeds() {
        assertEquals(0, Jobstein.commandLine().execute("--help"));
    }

    @Test void currentShowsEnvironmentAndBoundProject() throws Exception {
        var environments = new EnvironmentService(root.resolve("environments"));
        environments.create("development");
        new ConfigurationRepository(environments.directory("development"))
            .save("google", new GoogleSettings("proj-jobstein-dev-515517"));
        var secrets = new PosixFileSecretStore(environments.directory("development").resolve("secrets"));
        secrets.put("secret://google/client", "client".getBytes(StandardCharsets.UTF_8));
        secrets.put("secret://google/refresh", "refresh".getBytes(StandardCharsets.UTF_8));
        var output = new StringWriter();
        var cli = new picocli.CommandLine(new EnvironmentCommand(environments, "current"));
        cli.setOut(new PrintWriter(output, true));
        assertEquals(0, cli.execute("--env", "development"));
        assertEquals("Environment: development\nDirectory: " + environments.directory("development") + "\nType: live\nGCP project: proj-jobstein-dev-515517\nGoogle OAuth credentials: configured\n", output.toString());
    }

    @Test void currentClassifiesSandboxWithoutProject() throws Exception {
        var environments=new EnvironmentService(root.resolve("environments"));
        environments.create("sandbox");
        var output=new StringWriter();
        var cli=new picocli.CommandLine(new EnvironmentCommand(environments,"current"));
        cli.setOut(new PrintWriter(output,true));
        assertEquals(0,cli.execute("--env","sandbox"));
        assertEquals("Environment: sandbox\nDirectory: " + environments.directory("sandbox") + "\nType: sandbox\nGCP project: (not configured)\nGoogle OAuth credentials: not configured\n",output.toString());
    }
}

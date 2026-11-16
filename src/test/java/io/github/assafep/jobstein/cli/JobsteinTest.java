package io.github.assafep.jobstein.cli;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

class JobsteinTest {
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
}

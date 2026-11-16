package io.github.assafep.jobstein.cli;

import picocli.CommandLine;
import java.io.PrintWriter;
import java.util.List;

public final class Jobstein {
    private static final String[][] GROUPS = {
        {"environment", "create", "list", "show", "validate"},
        {"google", "configure", "authorize", "status", "validate"},
        {"ai", "configure", "validate"},
        {"user", "initialize", "show", "validate"},
        {"profile", "create", "list", "show", "validate", "activate", "deactivate"}
    };

    @CommandLine.Command(name = "jobstein", version = "0.1.0-SNAPSHOT",
        mixinStandardHelpOptions = true, description = "Jobstein administrative CLI")
    static final class Root implements Runnable {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        public void run() { spec.commandLine().usage(spec.commandLine().getOut()); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    static final class Pending implements java.util.concurrent.Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = "--env", defaultValue = "production")
        String environment;
        public Integer call() {
            spec.commandLine().getErr().println(
                spec.qualifiedName() + ": not implemented yet (environment: " + environment + ")");
            return 3;
        }
    }

    static CommandLine group(String name) {
        return new CommandLine(new Root()).setCommandName(name);
    }

    static void addLeaves(CommandLine parent, String... names) {
        for (String name : names) {
            parent.addSubcommand(name, new CommandLine(new Pending()).setCommandName(name));
        }
    }

    static void addGroup(CommandLine parent, String name, String... leaves) {
        CommandLine child = group(name);
        addLeaves(child, leaves);
        parent.addSubcommand(name, child);
    }

    public static CommandLine commandLine() {
        CommandLine root = new CommandLine(new Root());
        addLeaves(root, "onboard", "doctor");
        for (String[] group : GROUPS) {
            addGroup(root, group[0], List.of(group).subList(1, group.length).toArray(String[]::new));
        }
        CommandLine ai = root.getSubcommands().get("ai");
        addGroup(ai, "provider", "list", "models");
        addGroup(ai, "integration", "create", "update", "list", "show", "validate", "remove");
        addGroup(ai, "agent", "configure", "list", "show", "reset");
        CommandLine profile = root.getSubcommands().get("profile");
        addGroup(profile, "resume", "upload", "update", "history");
        addGroup(profile, "descriptor", "show", "regenerate", "validate");
        return root;
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}

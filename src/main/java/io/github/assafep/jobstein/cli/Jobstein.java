package io.github.assafep.jobstein.cli;

import picocli.CommandLine;
import java.util.List;

public final class Jobstein {
    private static final String[][] GROUPS = {
        
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

    static CommandLine group(String name) {
        return new CommandLine(new Root()).setCommandName(name);
    }

    static void addLeaves(CommandLine parent, String... names) {
        for (String name : names) {
            parent.addSubcommand(name, new CommandLine(new AdminCommand(parent.getCommandName().equals("jobstein")?name:parent.getCommandSpec().qualifiedName(" ").replaceFirst("^jobstein ", "")+" "+name)).setCommandName(name));
        }
    }

    static void addGroup(CommandLine parent, String name, String... leaves) {
        CommandLine child = group(name);
        parent.addSubcommand(name, child);
        addLeaves(child, leaves);
    }

    public static CommandLine commandLine() {
        CommandLine root = new CommandLine(new Root());
        addLeaves(root, "onboard", "doctor");
        for (String[] group : GROUPS) {
            addGroup(root, group[0], List.of(group).subList(1, group.length).toArray(String[]::new));
        }
        var home = java.nio.file.Path.of(System.getProperty("user.home"), ".config", "jobstein", "environments");
        var environments = new io.github.assafep.jobstein.environment.EnvironmentService(home);
        CommandLine env = group("environment");
        for (String operation : List.of("create", "list", "show", "validate")) {
            env.addSubcommand(operation, new CommandLine(new EnvironmentCommand(environments, operation)).setCommandName(operation));
        }
        root.addSubcommand("environment", env);
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

package io.github.assafep.jobstein.sandbox;

import io.github.assafep.jobstein.provisioning.OnboardingService;
import java.io.IOException;
import java.util.Set;
import java.util.Optional;
import java.util.function.Consumer;

/** Per-invocation failure injection; never used by live infrastructure. */
public final class SandboxFailure {
    private static final Set<String> STEPS=Set.of("google-configure","google-authorize","ai-configure","user-initialize","first-profile");
    private final Optional<String> target;
    private SandboxFailure(Optional<String> target){this.target=target;}
    public static SandboxFailure parse(String target){
        if(target!=null&&!STEPS.contains(target))throw new IllegalArgumentException("Unknown sandbox failure checkpoint");
        return new SandboxFailure(Optional.ofNullable(target));
    }
    public OnboardingService.Action wrap(String step,OnboardingService.Action action,Consumer<String> progress){
        return ()->{
            if(target.filter(step::equals).isPresent()){
                progress.accept("SIMULATED FAILURE: "+step);
                throw new IOException("Injected sandbox failure");
            }
            action.run();
        };
    }
}

package io.github.assafep.jobstein.provisioning;

import java.util.Objects;
import java.util.function.Consumer;

public final class OnboardingService {
    @FunctionalInterface public interface Action { void run() throws Exception; }
    public record Steps(Action googleConfigure,Action googleAuthorize,Action aiConfigure,Action userInitialize,Action firstProfile) {
        public Steps {
            Objects.requireNonNull(googleConfigure);Objects.requireNonNull(googleAuthorize);
            Objects.requireNonNull(aiConfigure);Objects.requireNonNull(userInitialize);Objects.requireNonNull(firstProfile);
        }
    }
    private final ProvisioningStateRepository state;
    private final Consumer<String> progress;
    public OnboardingService(ProvisioningStateRepository state,Consumer<String> progress){
        this.state=Objects.requireNonNull(state);this.progress=Objects.requireNonNull(progress);
    }
    public void run(Steps steps)throws Exception{
        run(steps,()->{});
    }
    public void run(Steps steps,Action validateCompletedFirstProfile)throws Exception{
        step("google-configure",steps.googleConfigure());
        step("google-authorize",steps.googleAuthorize());
        step("ai-configure",steps.aiConfigure());
        step("user-initialize",steps.userInitialize());
        if(state.completed("first-profile")){
            validateCompletedFirstProfile.run();
            progress.accept("Previously completed: first-profile");
        }else step("first-profile",steps.firstProfile());
    }
    private void step(String id,Action action)throws Exception{
        if(state.completed(id)){progress.accept("Previously completed: "+id);return;}
        state.record(id,ProvisioningStateRepository.Status.IN_PROGRESS);
        try{action.run();state.record(id,ProvisioningStateRepository.Status.COMPLETED);progress.accept("Completed: "+id);}
        catch(Exception e){
            try{state.record(id,ProvisioningStateRepository.Status.FAILED);}catch(Exception persistence){e.addSuppressed(persistence);}
            throw e;
        }
    }
}

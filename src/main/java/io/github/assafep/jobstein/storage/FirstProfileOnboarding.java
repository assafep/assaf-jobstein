package io.github.assafep.jobstein.storage;

import io.github.assafep.jobstein.provisioning.ProvisioningStateRepository;
import io.github.assafep.jobstein.provisioning.ProvisioningStateRepository.FirstProfile;
import io.github.assafep.jobstein.domain.ApplicantProfile.Status;
import io.github.assafep.jobstein.validation.DiagnosticException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Durable first-profile selection; callers hold the environment's single-writer lock. */
public final class FirstProfileOnboarding {
    @FunctionalInterface public interface RoleSource {String get()throws Exception;}
    private final ProvisioningStateRepository repository;
    private final UserProfileService service;
    private final Consumer<String> progress;
    public FirstProfileOnboarding(ProvisioningStateRepository repository,UserProfileService service){
        this(repository,service,message->{});
    }
    public FirstProfileOnboarding(ProvisioningStateRepository repository,UserProfileService service,Consumer<String> progress){
        this.repository=Objects.requireNonNull(repository);this.service=Objects.requireNonNull(service);
        this.progress=Objects.requireNonNull(progress);
    }
    private FirstProfile selection(Optional<String> requested,RoleSource source)throws Exception{
        var saved=repository.firstProfile();
        if(saved.isPresent()){
            if(requested.isPresent()&&!requested.get().equals(saved.get().role()))throw new IOException("Onboarding is bound to another target role");
            return saved.get();
        }
        var profiles=service.profiles().entries();
        // Legacy checkpoints did not retain a role. Adopt only an unambiguous existing profile.
        String role;
        if(requested.isPresent())role=requested.get();
        else if(repository.completed("first-profile")&&profiles.size()==1)role=profiles.values().iterator().next().profile().targetRole();
        else role=source.get();
        String slug=UserProfileService.slug(role);
        var existing=profiles.get(slug);
        if(repository.completed("first-profile")&&existing==null)throw new IOException("Completed onboarding profile is missing; select an existing role");
        var selected=new FirstProfile(role,slug,existing==null?UUID.randomUUID():existing.profile().id());
        if(existing!=null)identity(selected,existing);
        repository.selectFirstProfile(selected);
        return selected;
    }
    private void identity(FirstProfile selected,UserProfileService.ProfileState state)throws Exception{
        var profile=state.profile();
        if(!selected.id().equals(profile.id())||!selected.role().equals(profile.targetRole())||!selected.slug().equals(profile.slug())||!service.user().id().equals(profile.userId()))
            throw new IOException("Selected onboarding profile identity changed");
    }
    private void ready(FirstProfile selected)throws Exception{
        var state=service.get(selected.slug());identity(selected,state);
        service.validateUser();
        if(state.profile().status()!=Status.ACTIVE)throw new DiagnosticException(DiagnosticException.Code.APPROVAL_REQUIRED);
        service.descriptor(selected.slug());
    }
    public void run(Optional<String> requested,RoleSource roleSource,Optional<Path> supplied,ProfileOnboarding.ResumeSource resumeSource,ProfileOnboarding.Consent consent,ProfileOnboarding.Review review)throws Exception{
        var selected=selection(requested,roleSource);
        var existing=service.profiles().entries().get(selected.slug());
        if(existing!=null)identity(selected,existing);
        if(!repository.completed("first-profile"))new ProfileOnboarding(service,progress).run(selected.role(),Optional.of(selected.id()),supplied,resumeSource,consent,review);
        ready(selected);
    }
}

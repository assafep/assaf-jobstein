package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.assafep.jobstein.domain.ApplicantProfile.Status;
import io.github.assafep.jobstein.validation.DiagnosticException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class ProfileOnboarding {
    @FunctionalInterface public interface ResumeSource {Path get()throws Exception;}
    @FunctionalInterface public interface Consent {boolean confirm()throws Exception;}
    @FunctionalInterface public interface Review {boolean approve(JsonNode descriptor)throws Exception;}
    private final UserProfileService service;
    private final Consumer<String> progress;
    public ProfileOnboarding(UserProfileService service){this(service,message->{});}
    public ProfileOnboarding(UserProfileService service, Consumer<String> progress){
        this.service=Objects.requireNonNull(service);
        this.progress=Objects.requireNonNull(progress);
    }
    public UserProfileService.ProfileState run(String role,Optional<Path> supplied,ResumeSource resumeSource,Consent consent,Review review)throws Exception{
        return run(role,Optional.empty(),supplied,resumeSource,consent,review);
    }
    public UserProfileService.ProfileState run(String role,Optional<UUID> expectedId,Optional<Path> supplied,ResumeSource resumeSource,Consent consent,Review review)throws Exception{
        var state=service.create(role,expectedId);
        String slug=state.profile().slug();
        // Finish an interrupted descriptor operation before selecting a new resume or reviewing.
        state=service.resumePendingDescriptor(slug).orElse(state);
        // Only explicitly supplied files may replace a previously uploaded resume.
        if(supplied.isPresent())state=service.upload(slug,supplied.get());
        else if(state.resumes().isEmpty())state=service.upload(slug,resumeSource.get());
        if(state.profile().status()==Status.ACTIVE){
            service.descriptor(slug);
            return state;
        }
        if(state.descriptorId()==null){
            if(!consent.confirm())throw new DiagnosticException(DiagnosticException.Code.AI_CONSENT_REQUIRED);
            state=service.generateStored(slug,progress);
        }
        if(review.approve(service.descriptor(slug))){
            service.activate(slug,true);
            state=service.get(slug);
        }
        return state;
    }
}

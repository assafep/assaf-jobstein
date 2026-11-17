package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.*;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.domain.JobsteinUser;
import io.github.assafep.jobstein.validation.DiagnosticException;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class ProfileIndexRecovery {
    @FunctionalInterface public interface IndexIdentity {String verifiedIndexId()throws Exception;}
    @FunctionalInterface public interface Owner {JobsteinUser get()throws IOException;}
    public enum Source { LOCAL, REMOTE }
    public record Snapshot(String indexId,String capturedAt,JsonNode local,JsonNode remote,boolean localPresent){}
    public record Status(boolean consistent,boolean localPresent,int localCount,int remoteCount,int changedEntries,boolean pendingUpdate){}
    private static final ObjectMapper JSON=new ObjectMapper();
    private final ConfigurationRepository repository;
    private final WorkspaceStorage storage;
    private final IndexIdentity identity;
    private final Owner owner;
    public ProfileIndexRecovery(ConfigurationRepository repository,WorkspaceStorage storage,IndexIdentity identity,Owner owner){
        this.repository=repository;this.storage=storage;this.identity=identity;this.owner=owner;
    }
    private Snapshot capture()throws Exception{
        String id=identity.verifiedIndexId();
        var local=repository.load("profiles",JsonNode.class);
        return new Snapshot(id,Instant.now().toString(),local.orElseGet(()->JSON.createObjectNode().set("entries",JSON.createObjectNode())),storage.readJson(id),local.isPresent());
    }
    private static JsonNode entries(JsonNode value)throws IOException{
        if(!value.isObject()||!value.path("entries").isObject())throw new IOException("Invalid profile index");
        return value.path("entries");
    }
    private Status summarize(Snapshot snapshot)throws IOException{
        var local=entries(snapshot.local());var remote=entries(snapshot.remote());
        var keys=new HashSet<String>();local.fieldNames().forEachRemaining(keys::add);remote.fieldNames().forEachRemaining(keys::add);
        int changed=0;
        for(String key:keys)if(!Objects.equals(local.get(key),remote.get(key)))changed++;
        return new Status(snapshot.local().equals(snapshot.remote()),snapshot.localPresent(),local.size(),remote.size(),changed,repository.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class).isPresent());
    }
    public Status status()throws Exception{return summarize(capture());}
    public enum Position { BEFORE, AFTER, UNEXPECTED, NOT_APPLICABLE }
    public enum RecoveryState { CLEAN, DIVERGED, RECOVERABLE, CONFLICT }
    public record Inspection(RecoveryState state,Position local,Position remote){}
    private static Position position(JsonNode actual,ProfileIndexSync.Intent intent){
        if(actual.equals(intent.after()))return Position.AFTER;
        if(actual.equals(intent.before()))return Position.BEFORE;
        return Position.UNEXPECTED;
    }
    private Inspection inspect(Snapshot snapshot)throws Exception{
        var pending=repository.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class);
        if(pending.isEmpty())return new Inspection(snapshot.local().equals(snapshot.remote())?RecoveryState.CLEAN:RecoveryState.DIVERGED,Position.NOT_APPLICABLE,Position.NOT_APPLICABLE);
        var intent=pending.get();
        var local=position(snapshot.local(),intent);
        var remote=position(snapshot.remote(),intent);
        if(!snapshot.indexId().equals(intent.indexId())||local==Position.UNEXPECTED||remote==Position.UNEXPECTED)
            return new Inspection(RecoveryState.CONFLICT,local,remote);
        try{validateCandidate(intent.before());validateCandidate(intent.after());}
        catch(Exception invalid){
            if(invalid instanceof InterruptedException){Thread.currentThread().interrupt();throw invalid;}
            return new Inspection(RecoveryState.CONFLICT,local,remote);
        }
        return new Inspection(RecoveryState.RECOVERABLE,local,remote);
    }
    public Inspection inspect()throws Exception{return inspect(capture());}
    public record ResumeResult(Inspection before,Optional<String> backup,boolean applied){}
    private record RecoveryBackup(Snapshot snapshot,ProfileIndexSync.Intent intent){}
    public ResumeResult resume(boolean apply)throws Exception{
        var snapshot=capture();
        var before=inspect(snapshot);
        if(!apply||before.state()==RecoveryState.CLEAN)return new ResumeResult(before,Optional.empty(),false);
        if(before.state()!=RecoveryState.RECOVERABLE)throw new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);
        var intent=repository.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class).orElseThrow();
        String backup="recovery-backup-"+UUID.randomUUID();
        repository.save(backup,new RecoveryBackup(snapshot,intent));
        var latest=capture();
        if(!snapshot.indexId().equals(latest.indexId())||!snapshot.local().equals(latest.local())||!snapshot.remote().equals(latest.remote())||
            snapshot.localPresent()!=latest.localPresent()||!repository.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class).equals(Optional.of(intent)))
            throw new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);
        new ProfileIndexSync(repository,storage,snapshot.indexId()).recoverAndValidate();
        return new ResumeResult(before,Optional.of(backup),true);
    }
    private String preserve(Snapshot snapshot)throws IOException{
        String name="index-backup-"+UUID.randomUUID();
        repository.save(name,snapshot);return name;
    }
    public String backup()throws Exception{return preserve(capture());}
    public Status validate()throws Exception{
        var report=status();
        if(!report.consistent()||report.pendingUpdate())throw new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);
        return report;
    }
    public record Result(Status before,Optional<String> backup,boolean applied){}
    private void validateCandidate(JsonNode candidate)throws Exception{
        entries(candidate);
        var profiles=JSON.treeToValue(candidate,UserProfileService.Profiles.class);
        var userId=owner.get().id();
        var ids=new HashSet<UUID>();
        for(var entry:profiles.entries().entrySet()){
            var state=entry.getValue();var profile=state.profile();
            if(!entry.getKey().equals(profile.slug())||!userId.equals(profile.userId())||!ids.add(profile.id()))
                throw new IOException("Invalid profile ownership or identity");
        }
    }
    public Result reconcile(Source source,boolean apply)throws Exception{
        Objects.requireNonNull(source);
        var snapshot=capture();var before=summarize(snapshot);
        if(before.pendingUpdate()&&apply)throw new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);
        if(before.consistent()||!apply)return new Result(before,Optional.empty(),false);
        if(source==Source.LOCAL&&!snapshot.localPresent())throw new IOException("Cannot select missing local index");
        var chosen=source==Source.LOCAL?snapshot.local():snapshot.remote();
        validateCandidate(chosen);
        String backup=preserve(snapshot);
        var latest=capture();
        if(!snapshot.indexId().equals(latest.indexId())||!snapshot.local().equals(latest.local())||
            !snapshot.remote().equals(latest.remote())||snapshot.localPresent()!=latest.localPresent())
            throw new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);
        if(source==Source.LOCAL)storage.updateJson(snapshot.indexId(),chosen);
        else repository.save("profiles",chosen);
        validate();
        return new Result(before,Optional.of(backup),true);
    }
}

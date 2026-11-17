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

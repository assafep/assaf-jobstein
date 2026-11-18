package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.*;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.domain.JobsteinUser;
import io.github.assafep.jobstein.validation.DiagnosticException;
import java.io.IOException;
import java.util.*;

final class ProfileIndexSync {
    static final String JOURNAL="profile-index-intent";
    private static final ObjectMapper JSON=new ObjectMapper();
    record Intent(String indexId,JsonNode before,JsonNode after){
        Intent{
            if(indexId==null||!indexId.matches("[a-zA-Z0-9_-]{1,256}"))throw new IllegalArgumentException("Invalid profile index ID");
            if(before==null||after==null||!before.isObject()||!after.isObject())throw new IllegalArgumentException("Invalid profile index journal");
            before=before.deepCopy();
            after=after.deepCopy();
        }
    }
    private final ConfigurationRepository repository;
    private final WorkspaceStorage storage;
    private final String indexId;
    ProfileIndexSync(ConfigurationRepository repository,WorkspaceStorage storage,String indexId){
        this.repository=repository;this.storage=storage;this.indexId=indexId;
    }
    private JsonNode local()throws IOException{
        return JSON.valueToTree(repository.load("profiles",UserProfileService.Profiles.class).orElse(new UserProfileService.Profiles(Map.of())));
    }
    private static DiagnosticException conflict(){return new DiagnosticException(DiagnosticException.Code.PROFILE_INDEX_DIVERGED);}
    private JsonNode remote()throws Exception{return storage.readJson(indexId);}
    private void validateIndex(JsonNode value)throws Exception{
        if(!value.isObject()||!value.path("entries").isObject())throw conflict();
        var profiles=JSON.treeToValue(value,UserProfileService.Profiles.class);
        var user=repository.load("user",JobsteinUser.class).orElseThrow(ProfileIndexSync::conflict);
        var ids=new HashSet<UUID>();
        for(var entry:profiles.entries().entrySet()){
            var state=entry.getValue();
            if(state==null||state.profile()==null||!entry.getKey().equals(state.profile().slug())
                ||!user.id().equals(state.profile().userId())||!ids.add(state.profile().id()))throw conflict();
        }
    }
    void validate()throws Exception{
        if(repository.load(JOURNAL,Intent.class).isPresent())throw conflict();
        var l=local();var r=remote();
        validateIndex(l);validateIndex(r);
        if(!l.equals(r))throw conflict();
    }
    void recoverAndValidate()throws Exception{
        var pending=repository.load(JOURNAL,Intent.class);
        if(pending.isPresent())finish(pending.get());
        validate();
    }
    void update(UserProfileService.Profiles profiles)throws Exception{
        recoverAndValidate();
        JsonNode before=local(),after=JSON.valueToTree(profiles);
        if(!before.equals(remote()))throw conflict();
        if(before.equals(after))return;
        var intent=new Intent(indexId,before,after);
        repository.save(JOURNAL,intent);
        finish(intent);
    }
    private void finish(Intent intent)throws Exception{
        if(!indexId.equals(intent.indexId()))throw conflict();
        var before=intent.before();var after=intent.after();
        if(before==null||after==null)throw conflict();
        validateIndex(before);
        validateIndex(after);
        var l=local();var r=remote();
        if(!(l.equals(before)||l.equals(after))||!(r.equals(before)||r.equals(after)))throw conflict();
        if(!r.equals(after)){
            if(!remote().equals(before))throw conflict();
            storage.updateJson(indexId,after);
        }
        if(!remote().equals(after))throw conflict();
        if(!local().equals(l))throw conflict();
        repository.save("profiles",after);
        if(!local().equals(after)||!remote().equals(after))throw conflict();
        repository.remove(JOURNAL);
    }
}

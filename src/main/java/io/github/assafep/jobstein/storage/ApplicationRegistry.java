package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class ApplicationRegistry {
    public enum Status { RESERVED, SUBMITTING, SUBMISSION_UNKNOWN, APPLIED, COMPLETED, CANCELLED_BEFORE_SUBMISSION }
    public interface Store {
        ObjectNode read() throws Exception;
        void write(ObjectNode state) throws Exception;
    }
    @FunctionalInterface public interface ProfileOwnership {
        void verify(UUID profileId) throws Exception;
    }
    private final Store store;
    private final ProfileOwnership ownership;
    public ApplicationRegistry(Store store){this(store,profileId->{});}
    public ApplicationRegistry(Store store,ProfileOwnership ownership){
        this.store=Objects.requireNonNull(store);this.ownership=Objects.requireNonNull(ownership);
    }
    public static void validate(ObjectNode state)throws IOException { entries(state); }
    private static ObjectNode entries(ObjectNode state)throws IOException{
        if(state==null || !state.path("schemaVersion").isIntegralNumber()
            || !state.path("schemaVersion").canConvertToInt() || state.path("schemaVersion").intValue()!=1
            || !(state.path("entries") instanceof ObjectNode entries))
            throw new IOException("Invalid application registry");
        var ids=new HashSet<UUID>();
        var fields=entries.fields();
        while(fields.hasNext()){
            var field=fields.next();
            var item=field.getValue();
            try{
                if(field.getKey().isBlank() || !item.isObject())throw new IllegalArgumentException();
                UUID id=uuid(item.path("applicationId"));
                uuid(item.path("profileId"));
                if(!ids.add(id))throw new IllegalArgumentException();
                Status.valueOf(requiredText(item.path("status")));
                Instant.parse(requiredText(item.path("updatedAt")));
                if(!item.path("aliases").isArray())throw new IllegalArgumentException();
                var aliases=new HashSet<String>();
                for(var alias:item.path("aliases"))
                    if(!aliases.add(requiredText(alias)))throw new IllegalArgumentException();
            }catch(RuntimeException invalid){
                // Do not include persisted posting identifiers or values in diagnostics.
                throw new IOException("Invalid application registry entry");
            }
        }
        return entries;
    }
    private static String requiredText(JsonNode value){
        if(!value.isTextual() || value.textValue().isBlank())throw new IllegalArgumentException();
        return value.textValue();
    }
    private static UUID uuid(JsonNode value){
        String text=requiredText(value);
        UUID id=UUID.fromString(text);
        if(!id.toString().equals(text))throw new IllegalArgumentException();
        return id;
    }
    public synchronized Optional<UUID> reserve(String canonicalId, Set<String> aliases, UUID profileId)throws Exception{
        if(canonicalId==null||canonicalId.isBlank()||profileId==null)throw new IllegalArgumentException("Posting and profile required");
        ownership.verify(profileId);
        var keys=new HashSet<>(Set.copyOf(aliases));keys.add(canonicalId);
        if(keys.stream().anyMatch(value->value==null||value.isBlank()))throw new IllegalArgumentException("Empty posting alias");
        var state=store.read();var all=entries(state);validateOwnership(all);
        var iterator=all.fields();
        while(iterator.hasNext()){
            var entry=iterator.next();var item=entry.getValue();
            if(Status.CANCELLED_BEFORE_SUBMISSION.name().equals(item.path("status").asText()))continue;
            if(keys.contains(entry.getKey()))return Optional.empty();
            for(var alias:item.path("aliases"))if(keys.contains(alias.asText()))return Optional.empty();
        }
        UUID id=UUID.randomUUID();
        var item=all.putObject(canonicalId).put("applicationId",id.toString()).put("profileId",profileId.toString())
            .put("status",Status.RESERVED.name()).put("updatedAt",Instant.now().toString());
        var array=item.putArray("aliases");keys.stream().sorted().forEach(array::add);
        store.write(state);
        return Optional.of(id);
    }
    public synchronized void transition(String canonicalId, UUID applicationId, Status next)throws Exception{
        var state=store.read();var all=entries(state);validateOwnership(all);
        if(!(all.path(canonicalId) instanceof ObjectNode item)||!applicationId.toString().equals(item.path("applicationId").asText()))
            throw new IOException("Application reservation does not match");
        Status old=Status.valueOf(item.path("status").asText());
        boolean allowed=switch(old){
            case RESERVED -> next==Status.SUBMITTING||next==Status.CANCELLED_BEFORE_SUBMISSION;
            case SUBMITTING -> next==Status.APPLIED||next==Status.SUBMISSION_UNKNOWN;
            case SUBMISSION_UNKNOWN -> next==Status.APPLIED||next==Status.CANCELLED_BEFORE_SUBMISSION;
            case APPLIED -> next==Status.COMPLETED;
            default -> false;
        };
        if(!allowed)throw new IOException("Illegal application state transition");
        item.put("status",next.name()).put("updatedAt",Instant.now().toString());
        store.write(state);
    }
    private void validateOwnership(ObjectNode entries)throws Exception{
        var fields=entries.fields();
        while(fields.hasNext())ownership.verify(uuid(fields.next().getValue().path("profileId")));
    }
}

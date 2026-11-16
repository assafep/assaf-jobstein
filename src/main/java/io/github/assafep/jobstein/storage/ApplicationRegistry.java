package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public final class ApplicationRegistry {
    public enum Status { RESERVED, SUBMITTING, SUBMISSION_UNKNOWN, APPLIED, COMPLETED, CANCELLED_BEFORE_SUBMISSION }
    public interface Store {
        ObjectNode read() throws Exception;
        void write(ObjectNode state) throws Exception;
    }
    private final Store store;
    public ApplicationRegistry(Store store){this.store=Objects.requireNonNull(store);}
    private static ObjectNode entries(ObjectNode state)throws IOException{
        if(state.path("schemaVersion").asInt()!=1 || !(state.path("entries") instanceof ObjectNode entries))
            throw new IOException("Invalid application registry");
        return entries;
    }
    public synchronized Optional<UUID> reserve(String canonicalId, Set<String> aliases, UUID profileId)throws Exception{
        if(canonicalId==null||canonicalId.isBlank()||profileId==null)throw new IllegalArgumentException("Posting and profile required");
        var keys=new HashSet<>(Set.copyOf(aliases));keys.add(canonicalId);
        if(keys.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Empty posting alias");
        var state=store.read();var all=entries(state);
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
        var state=store.read();var all=entries(state);
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
}

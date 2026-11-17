package io.github.assafep.jobstein.storage;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationRegistryTest {
    static class Memory implements ApplicationRegistry.Store{
        ObjectNode state=new ObjectMapper().createObjectNode().put("schemaVersion",1);
        Memory(){state.putObject("entries");}
        public ObjectNode read(){return state.deepCopy();}
        public void write(ObjectNode next){state=next.deepCopy();}
    }
    @Test void duplicateAcrossProfilesAndAliases()throws Exception{
        var registry=new ApplicationRegistry(new Memory());
        var id=registry.reserve("ats:company:123",Set.of("board:company:999"),UUID.randomUUID()).orElseThrow();
        assertTrue(registry.reserve("board:company:999",Set.of(),UUID.randomUUID()).isEmpty());
        registry.transition("ats:company:123",id,ApplicationRegistry.Status.SUBMITTING);
        registry.transition("ats:company:123",id,ApplicationRegistry.Status.SUBMISSION_UNKNOWN);
        assertTrue(registry.reserve("ats:company:123",Set.of(),UUID.randomUUID()).isEmpty());
        registry.transition("ats:company:123",id,ApplicationRegistry.Status.APPLIED);
        registry.transition("ats:company:123",id,ApplicationRegistry.Status.COMPLETED);
        assertTrue(registry.reserve("ats:company:123",Set.of(),UUID.randomUUID()).isEmpty());
    }
    @Test void rejectsInvalidTransitions()throws Exception{
        var registry=new ApplicationRegistry(new Memory());
        var id=registry.reserve("ats:company:123",Set.of(),UUID.randomUUID()).orElseThrow();
        assertThrows(java.io.IOException.class,()->registry.transition("ats:company:123",id,ApplicationRegistry.Status.APPLIED));
    }
    @Test void malformedPersistedEntriesBlockAllMutations()throws Exception{
        var corruptions=List.<java.util.function.Consumer<ObjectNode>>of(
            item->item.put("applicationId","1-1-1-1-1"),
            item->item.put("profileId","invalid"),
            item->item.put("status","UNRECOGNIZED"),
            item->item.put("updatedAt","yesterday"),
            item->item.put("aliases","board:synthetic:1"),
            item->item.putArray("aliases").add(42),
            item->item.putArray("aliases").add(" "),
            item->item.putArray("aliases").add("alias").add("alias"));
        for(var corrupt:corruptions){
            var memory=new Memory();
            var registry=new ApplicationRegistry(memory);
            var id=registry.reserve("ats:synthetic:1",Set.of("board:synthetic:1"),UUID.randomUUID()).orElseThrow();
            corrupt.accept((ObjectNode)memory.state.path("entries").path("ats:synthetic:1"));
            var before=memory.state.deepCopy();
            assertThrows(java.io.IOException.class,()->registry.reserve("different",Set.of(),UUID.randomUUID()));
            assertThrows(java.io.IOException.class,()->registry.transition("ats:synthetic:1",id,ApplicationRegistry.Status.SUBMITTING));
            assertEquals(before,memory.state);
        }
    }
    @Test void validatesWholeRegistryIncludingCancelledEntries()throws Exception{
        var memory=new Memory();
        var registry=new ApplicationRegistry(memory);
        var id=registry.reserve("first",Set.of(),UUID.randomUUID()).orElseThrow();
        registry.transition("first",id,ApplicationRegistry.Status.CANCELLED_BEFORE_SUBMISSION);
        ApplicationRegistry.validate(memory.state);
        var entries=(ObjectNode)memory.state.path("entries");
        entries.set("second",entries.path("first").deepCopy());
        assertThrows(java.io.IOException.class,()->ApplicationRegistry.validate(memory.state));
        entries.remove("second");
        ((ObjectNode)entries.path("first")).remove("profileId");
        assertThrows(java.io.IOException.class,()->registry.reserve("first",Set.of(),UUID.randomUUID()));
    }
    @Test void rejectsCoercedSchemaVersions()throws Exception{
        for(var version:List.of(new ObjectMapper().readTree("\"1\""),new ObjectMapper().readTree("1.5"))){
            var memory=new Memory();memory.state.set("schemaVersion",version);
            assertThrows(java.io.IOException.class,()->ApplicationRegistry.validate(memory.state));
        }
    }
}

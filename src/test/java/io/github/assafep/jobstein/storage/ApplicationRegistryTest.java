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
}

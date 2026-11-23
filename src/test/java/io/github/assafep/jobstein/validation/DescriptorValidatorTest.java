package io.github.assafep.jobstein.validation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DescriptorValidatorTest {
    private static final String SOURCE="Software Engineer at Example Co\nJava and Scala\nBSc Computer Science";
    private ObjectNode descriptor(){
        var d=new ObjectMapper().createObjectNode().put("professionalSummary","Software Engineer at Example Co");
        d.putArray("skills").add("Java");
        d.putArray("skillEvidence").add("Java");
        d.putArray("employmentHistory").addObject().put("excerpt","Software Engineer at Example Co").put("evidence","Software Engineer at Example Co");
        d.putArray("education").addObject().put("excerpt","BSc Computer Science").put("evidence","BSc Computer Science");
        return d;
    }
    @Test void acceptsSourceExcerpts()throws Exception{DescriptorValidator.validate(descriptor(),SOURCE);}
    @Test void generatesSkillsFromEvidenceAndKeepsStoredValidationStrict() throws Exception {
        var response = descriptor();
        response.putArray("skills").add("Java development");
        assertThrows(DiagnosticException.class, () -> DescriptorValidator.validate(response, SOURCE));
        var prepared = DescriptorValidator.prepareGenerated(response, SOURCE);
        assertEquals(prepared.path("skillEvidence"), prepared.path("skills"));
        assertEquals("Java", prepared.path("skills").get(0).asText());
        assertEquals("Java development", response.path("skills").get(0).asText());
        response.remove("skills");
        assertEquals(prepared, DescriptorValidator.prepareGenerated(response, SOURCE));
    }

    @Test void rejectsUnsupportedEvidenceWhenPreparingGeneratedSkills() {
        var response = descriptor();
        response.putArray("skillEvidence").add("invented skill");
        assertThrows(DiagnosticException.class, () -> DescriptorValidator.prepareGenerated(response, SOURCE));
    }
    @Test void rejectsInventedSkills(){
        var d=descriptor();d.putArray("skills").add("Kubernetes");d.putArray("skillEvidence").add("Kubernetes");
        var error=assertThrows(DiagnosticException.class,()->DescriptorValidator.validate(d,SOURCE));
        String output=Diagnostics.describe(error);
        assertTrue(output.startsWith("[DESCRIPTOR_INVALID] skillEvidence[0] does not match a verbatim excerpt"));
        assertFalse(output.contains("Kubernetes"));
        assertFalse(output.contains("Example Co"));
    }
    @Test void rejectsUnrelatedEvidence(){
        var d=descriptor();d.putArray("skills").add("Kubernetes");
        assertThrows(IOException.class,()->DescriptorValidator.validate(d,SOURCE));
    }
    @Test void rejectsUnexpectedClaims(){
        var d=descriptor();d.put("yearsOfExperience",20);
        var error=assertThrows(DiagnosticException.class,()->DescriptorValidator.validate(d,SOURCE));
        assertTrue(Diagnostics.describe(error).contains("field outside the schema"));
        assertFalse(Diagnostics.describe(error).contains("yearsOfExperience"));
    }
    @Test void rejectsWrongProfile(){
        var id=UUID.randomUUID();
        var envelope=new ObjectMapper().createObjectNode().put("schemaVersion",2).put("profileId",UUID.randomUUID().toString()).put("resumeSha256","hash");
        assertThrows(IOException.class,()->DescriptorValidator.validateEnvelope(envelope,id,"hash"));
    }
}

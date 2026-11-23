package io.github.assafep.jobstein.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.*;

public final class DescriptorValidator {
    private static final Set<String> FIELDS=Set.of("professionalSummary","skills","skillEvidence","employmentHistory","education","synthetic");
    private DescriptorValidator(){}
    private static IOException invalid(String reason){return DiagnosticException.invalidDescriptor(reason);}
    private static String normalize(String value){return value.replaceAll("(?U)\\s+"," ").strip();}
    private static String text(JsonNode value,String field)throws IOException{
        if(!value.isTextual()||normalize(value.asText()).isEmpty())throw invalid(field+" must contain non-empty text.");
        return normalize(value.asText());
    }
    private static String supported(JsonNode value,String source,String field)throws IOException{
        String excerpt=text(value,field);
        if(!source.contains(excerpt))throw invalid(field+" does not match a verbatim excerpt in the extracted resume text.");
        return excerpt;
    }
    private static JsonNode array(JsonNode node,String field)throws IOException{
        var value=node.path(field);
        if(!value.isArray())throw invalid(field+" must be an array.");
        return value;
    }

    /** Build the redundant skills array before validating and persisting a new AI response. */
    public static ObjectNode prepareGenerated(JsonNode response, String resumeText) throws IOException {
        if (!(response instanceof ObjectNode object)) throw invalid("Expected a JSON object.");
        ObjectNode descriptor = object.deepCopy();
        var evidence = array(descriptor, "skillEvidence");
        var skills = descriptor.putArray("skills");
        evidence.forEach(skills::add);
        validate(descriptor, resumeText);
        return descriptor;
    }

    public static void validate(JsonNode descriptor,String resumeText)throws IOException{
        if(!descriptor.isObject())throw invalid("Expected a JSON object.");
        var keys=descriptor.fieldNames();
        while(keys.hasNext())if(!FIELDS.contains(keys.next()))throw invalid("The descriptor contains a field outside the schema.");
        if(descriptor.has("synthetic")&&!descriptor.path("synthetic").isBoolean())throw invalid("synthetic must be a boolean.");
        String source=normalize(resumeText);
        supported(descriptor.path("professionalSummary"),source,"professionalSummary");
        var skills=array(descriptor,"skills");
        var evidence=array(descriptor,"skillEvidence");
        if(skills.size()!=evidence.size())throw invalid("skills and skillEvidence must have the same number of entries.");
        for(int i=0;i<skills.size();i++){
            String excerpt=supported(evidence.get(i),source,"skillEvidence["+i+"]");
            if(!excerpt.equals(text(skills.get(i),"skills["+i+"]")))throw invalid("skills["+i+"] must equal skillEvidence["+i+"].");
        }
        for(String field:List.of("employmentHistory","education")){
            var entries=array(descriptor,field);
            for(int i=0;i<entries.size();i++){
                var entry=entries.get(i);
                String path=field+"["+i+"]";
                if(!entry.isObject()||entry.size()!=2)throw invalid(path+" must contain exactly excerpt and evidence.");
                String evidenceText=supported(entry.path("evidence"),source,path+".evidence");
                if(!evidenceText.equals(text(entry.path("excerpt"),path+".excerpt")))throw invalid(path+".excerpt must equal its evidence.");
            }
        }
    }
    public static void validateEnvelope(JsonNode envelope,UUID profileId,String hash)throws IOException{
        if(!envelope.isObject()||!envelope.path("schemaVersion").isIntegralNumber()||envelope.path("schemaVersion").asInt()!=2)
            throw invalid("The stored descriptor schema version is unsupported.");
        if(!profileId.toString().equals(envelope.path("profileId").asText())||!hash.equals(envelope.path("resumeSha256").asText()))
            throw invalid("The stored descriptor belongs to a different profile or resume version.");
        for(String field:List.of("integrationId","provider","model"))text(envelope.path(field),field);
    }
}

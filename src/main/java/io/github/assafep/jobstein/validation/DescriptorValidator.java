package io.github.assafep.jobstein.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;

public final class DescriptorValidator {
    private static final Set<String> FIELDS=Set.of("professionalSummary","skills","skillEvidence","employmentHistory","education","synthetic");
    private DescriptorValidator(){}
    private static IOException invalid(String reason){return new DiagnosticException(DiagnosticException.Code.DESCRIPTOR_INVALID,new IOException(reason));}
    private static String normalize(String value){return value.replaceAll("(?U)\\s+"," ").strip();}
    private static String text(JsonNode value)throws IOException{
        if(!value.isTextual()||normalize(value.asText()).isEmpty())throw invalid("empty or non-text evidence");
        return normalize(value.asText());
    }
    private static void supported(JsonNode value,String source)throws IOException{
        if(!source.contains(text(value)))throw invalid("excerpt absent from resume");
    }
    private static JsonNode array(JsonNode node,String field)throws IOException{
        var value=node.path(field);
        if(!value.isArray())throw invalid("missing array "+field);
        return value;
    }
    public static void validate(JsonNode descriptor,String resumeText)throws IOException{
        if(!descriptor.isObject())throw invalid("expected object");
        var keys=descriptor.fieldNames();
        while(keys.hasNext())if(!FIELDS.contains(keys.next()))throw invalid("unexpected field");
        if(descriptor.has("synthetic")&&!descriptor.path("synthetic").isBoolean())throw invalid("invalid synthetic marker");
        String source=normalize(resumeText);
        supported(descriptor.path("professionalSummary"),source);
        var skills=array(descriptor,"skills");
        var evidence=array(descriptor,"skillEvidence");
        if(skills.size()!=evidence.size())throw invalid("skill evidence count mismatch");
        for(int i=0;i<skills.size();i++){
            supported(evidence.get(i),source);
            if(!text(evidence.get(i)).equals(text(skills.get(i))))throw invalid("skill must equal its evidence");
        }
        for(String field:List.of("employmentHistory","education")){
            for(var entry:array(descriptor,field)){
                if(!entry.isObject()||entry.size()!=2)throw invalid("unexpected evidence record fields");
                supported(entry.path("evidence"),source);
                if(!text(entry.path("evidence")).equals(text(entry.path("excerpt"))))throw invalid("record must equal its evidence");
            }
        }
    }
    public static void validateEnvelope(JsonNode envelope,UUID profileId,String hash)throws IOException{
        if(!envelope.isObject()||!envelope.path("schemaVersion").isIntegralNumber()||envelope.path("schemaVersion").asInt()!=2)
            throw invalid("unsupported schema; regenerate descriptor");
        if(!profileId.toString().equals(envelope.path("profileId").asText())||!hash.equals(envelope.path("resumeSha256").asText()))
            throw invalid("profile or resume identity mismatch");
        for(String field:List.of("integrationId","provider","model"))text(envelope.path(field));
    }
}

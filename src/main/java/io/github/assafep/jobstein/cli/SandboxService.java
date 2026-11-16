package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.environment.EnvironmentService;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import io.github.assafep.jobstein.storage.ResumeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.assafep.jobstein.ai.*;

final class SandboxService {
    public record State(Map<String,String> profiles, Map<String,String> bindings) {
        public State { profiles=Map.copyOf(profiles); bindings=Map.copyOf(bindings); }
    }
    public record ResumeState(List<Version> versions, String descriptorHash, String integrationId, String model) {
        public ResumeState { versions=List.copyOf(versions); }
    }
    public record Version(String sha256,String fixtureName) {}
    private ResumeState resumeState(String profile)throws IOException {
        return repository.load("resume-"+profile.replace('.','-'),ResumeState.class)
            .orElseGet(()->new ResumeState(List.of(new Version(ResumeDocument.sha256(("synthetic:"+profile).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"built-in synthetic resume")),"fixture","standard","mock-grok"));
    }
    private void saveResume(String profile,ResumeState state)throws IOException{
        repository.save("resume-"+profile.replace('.','-'),state);
    }
    private static void validateResume(ResumeState state)throws IOException{
        if(state.descriptorHash()==null||state.versions().isEmpty()||
            (!state.descriptorHash().equals("fixture")&&!state.descriptorHash().equals(state.versions().getLast().sha256())))
            throw new IOException("Descriptor missing or stale; regenerate it");
    }
    private void upload(String key,Path file,Map<String,String> profiles,Map<String,String> bindings)throws IOException{
        var previous=resumeState(key);
        String hash;
        String name;
        if(file==null){
            // Deterministic fixture; a new version is requested by a separate explicit upload.
            hash=ResumeDocument.sha256(("synthetic:"+key+":"+previous.versions().size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            name="synthetic resume "+(previous.versions().size()+1);
        }else{
            var document=ResumeDocument.read(file);
            hash=document.sha256();
            name="local PDF fixture"; // Do not persist names or personal text in simulation.
        }
        if(previous.versions().getLast().sha256().equals(hash))return;
        var versions=new ArrayList<>(previous.versions());versions.add(new Version(hash,name));
        saveResume(key,new ResumeState(versions,null,null,null));
        profiles.put(key,"RESUME_UPLOADED");save(new State(profiles,bindings));
    }
    private final EnvironmentService environments;
    private final ConfigurationRepository repository;
    private final PrintWriter out;
    SandboxService(EnvironmentService environments, PrintWriter out) {
        this.environments=environments; this.out=out;
        this.repository=new ConfigurationRepository(environments.directory("sandbox").resolve("simulation"));
    }
    private State load() throws IOException {
        return repository.load("state",State.class).orElseThrow(()->new IOException("Run onboard --env sandbox first"));
    }
    private void save(State state)throws IOException{repository.save("state",state);}
    private AiConfiguration aiConfig() throws IOException {
        return repository.load("mock-ai", AiConfiguration.class).orElseGet(() ->
            new AiConfiguration(1,"standard",Map.of("standard", mock("mock-grok")),Map.of()));
    }
    private static AiIntegrationConfig mock(String model) {
        if(!Set.of("mock-grok","mock-grok-advanced").contains(model))
            throw new IllegalArgumentException("Unknown synthetic model");
        return new AiIntegrationConfig("mock-xai",Map.of("model",model));
    }
    private void saveAi(AiConfiguration config)throws IOException {repository.save("mock-ai",config);}
    private static AiIntegrationConfig selected(AiConfiguration config,String integration){
        var result=config.integrations().get(integration==null?config.defaultIntegration():integration);
        if(result==null)throw new IllegalArgumentException("Unknown integration");
        return result;
    }
    private void changeAi(String op,String integration,Map<String,String> fields,boolean makeDefault)throws IOException {
        var config=aiConfig();
        String id=integration==null&&op.equals("ai configure")?config.defaultIntegration():require(integration,"--integration");
        if(!id.matches("[a-z][a-z0-9-]*"))throw new IllegalArgumentException("Invalid integration ID");
        var values=new TreeMap<>(config.integrations());
        if(op.equals("ai integration remove")){
            if(values.remove(id)==null)throw new IllegalArgumentException("Unknown integration");
        }else{
            boolean exists=values.containsKey(id);
            if(op.equals("ai integration create")&&exists)throw new IllegalArgumentException("Integration already exists");
            if(op.equals("ai integration update")&&!exists)throw new IllegalArgumentException("Unknown integration");
            if(!fields.keySet().stream().allMatch("model"::equals))throw new IllegalArgumentException("Sandbox accepts only a synthetic model field");
            String model=fields.getOrDefault("model",exists?values.get(id).configuration().get("model"):"mock-grok");
            values.put(id,mock(model));
        }
        saveAi(new AiConfiguration(1,makeDefault&&!op.endsWith("remove")?id:config.defaultIntegration(),values,config.agentBindings()));
    }

    private static String require(String value,String label){
        if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");
        return value;
    }
    private static String slug(String role){return io.github.assafep.jobstein.storage.UserProfileService.slug(role);}
    int execute(String op,String role,String profile,String agent,String integration,Map<String,String> fields,boolean makeDefault)throws IOException{
        out.println("SANDBOX — simulated data; no external API calls");
        if(op.equals("onboard")){
            environments.create("sandbox");
            if(repository.load("state",State.class).isEmpty())save(new State(Map.of("software.engineer","ACTIVE"),Map.of()));
            out.println("Simulated GCP, OAuth, AI, Drive, user and first profile initialized");
            return 0;
        }
        var state=load();
        var profiles=new TreeMap<>(state.profiles());
        var bindings=new TreeMap<>(state.bindings());
        var ai=aiConfig();
        switch(op){
            case "doctor" -> {
                environments.validate("sandbox");
                for(String label:List.of("Google project","OAuth","Workspace","AI integration","Drive registry"))out.println("SIMULATED "+label);
                boolean active=profiles.containsValue("ACTIVE");
                for(var entry:profiles.entrySet())if(entry.getValue().equals("ACTIVE"))validateResume(resumeState(entry.getKey()));
                out.println(active?"PASS Sandbox fixture ready":"FAIL No active sandbox profile");
                return active?0:1;
            }
            case "google status","google validate","google configure","google authorize" -> out.println("Simulated Google project: jobstein-sandbox");
            case "ai provider list" -> out.println("mock-xai");
            case "ai provider models" -> {out.println("mock-grok");out.println("mock-grok-advanced");}
            case "ai integration list" -> new TreeMap<>(ai.integrations()).forEach((id,value)->out.println(id+" "+value.provider()+" "+value.configuration().get("model")));
            case "ai integration show","ai integration validate" -> out.println(selected(ai,integration));
            case "ai validate" -> out.println("Simulated configuration valid");
            case "ai configure","ai integration create","ai integration update","ai integration remove" -> changeAi(op,integration,fields,makeDefault);
            case "ai agent list" -> {out.println("Default: "+ai.defaultIntegration());new TreeMap<>(ai.agentBindings()).forEach((a,i)->out.println(a+" -> "+i));}
            case "ai agent show" -> out.println(new AiIntegrationResolver(ai).resolve(require(agent,"--agent")).id());
            case "ai agent configure" -> {
                var updated=new TreeMap<>(ai.agentBindings());updated.put(require(agent,"--agent"),require(integration,"--integration"));
                saveAi(new AiConfiguration(1,ai.defaultIntegration(),ai.integrations(),updated));
            }
            case "ai agent reset" -> {var updated=new TreeMap<>(ai.agentBindings());updated.remove(require(agent,"--agent"));saveAi(new AiConfiguration(1,ai.defaultIntegration(),ai.integrations(),updated));}
            case "user show","user initialize","user validate" -> out.println("Example Applicant (synthetic)");
            case "profile list" -> profiles.forEach((k,v)->out.println(k+" "+v));
            case "profile create" -> {
                String key=slug(require(role,"--role"));profiles.putIfAbsent(key,"ACTIVE");save(new State(profiles,bindings));if(resume!=null)upload(key,resume,profiles,bindings);out.println(key+" "+profiles.get(key));
            }
            case "profile show","profile validate","profile descriptor show","profile descriptor validate","profile resume history" -> {
                String key=require(profile,"--profile");
                if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                var current=resumeState(key);
                if(op.startsWith("profile descriptor")||op.equals("profile validate")){
                    validateResume(current);
                    var json=new ObjectMapper().createObjectNode().put("synthetic",true).put("professionalSummary","Example applicant")
                        .put("resumeSha256",current.versions().getLast().sha256()).put("integrationId",current.integrationId()).put("model",current.model());
                    json.putArray("skills").add("Java");json.putArray("employmentHistory");json.putArray("education");
                    out.println(json.toPrettyString());
                }else if(op.equals("profile resume history"))current.versions().forEach(out::println);
                else out.println(key+" "+profiles.get(key));
            }
            case "profile resume upload","profile resume update" -> {
                String key=require(profile,"--profile");if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                upload(key,resume,profiles,bindings);
            }
            case "profile descriptor regenerate" -> {
                String key=require(profile,"--profile");if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                var current=resumeState(key);
                if(resume!=null&&!ResumeDocument.read(resume).sha256().equals(current.versions().getLast().sha256()))
                    throw new IOException("Resume does not match latest version");
                var resolved=new AiIntegrationResolver(ai).resolve("applicant-profile-generator");
                saveResume(key,new ResumeState(current.versions(),current.versions().getLast().sha256(),resolved.id(),resolved.configuration().configuration().get("model")));
                profiles.put(key,approveDescriptor?"ACTIVE":"AWAITING_REVIEW");save(new State(profiles,bindings));
                out.println(key+" "+profiles.get(key));
            }
            case "profile activate","profile deactivate" -> {
                String key=require(profile,"--profile");if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                if(op.equals("profile activate")){
                    validateResume(resumeState(key));
                    if(!approveDescriptor)throw new IllegalArgumentException("Use --approve-descriptor to approve the synthetic descriptor");
                }
                profiles.put(key,op.equals("profile activate")?"ACTIVE":"INACTIVE");save(new State(profiles,bindings));
            }
            default -> {out.println("Not supported in sandbox: "+op);return 3;}
        }
        return 0;
    }
}

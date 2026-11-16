package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.environment.EnvironmentService;
import java.io.*;
import java.util.*;

final class SandboxService {
    public record State(Map<String,String> profiles, Map<String,String> bindings) {
        public State { profiles=Map.copyOf(profiles); bindings=Map.copyOf(bindings); }
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
    private static String require(String value,String label){
        if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");
        return value;
    }
    private static String slug(String role){return io.github.assafep.jobstein.storage.UserProfileService.slug(role);}
    int execute(String op,String role,String profile,String agent,String integration)throws IOException{
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
        switch(op){
            case "doctor" -> {
                environments.validate("sandbox");
                for(String label:List.of("Google project","OAuth","Workspace","AI integration","Drive registry"))out.println("SIMULATED "+label);
                boolean active=profiles.containsValue("ACTIVE");
                out.println(active?"PASS Sandbox fixture ready":"FAIL No active sandbox profile");
                return active?0:1;
            }
            case "google status","google validate","google configure","google authorize" -> out.println("Simulated Google project: jobstein-sandbox");
            case "ai provider list" -> out.println("mock-xai");
            case "ai provider models" -> out.println("mock-grok");
            case "ai integration list","ai integration show","ai integration validate","ai validate" -> out.println("standard mock-xai mock-grok");
            case "ai agent list" -> {out.println("Default: standard");bindings.forEach((a,i)->out.println(a+" -> "+i));}
            case "ai agent show" -> out.println(bindings.getOrDefault(require(agent,"--agent"),"standard"));
            case "ai agent configure" -> {
                if(!"standard".equals(integration))throw new IllegalArgumentException("Sandbox supports only standard integration");
                bindings.put(require(agent,"--agent"),integration);save(new State(profiles,bindings));
            }
            case "ai agent reset" -> {bindings.remove(require(agent,"--agent"));save(new State(profiles,bindings));}
            case "user show","user initialize","user validate" -> out.println("Example Applicant (synthetic)");
            case "profile list" -> profiles.forEach((k,v)->out.println(k+" "+v));
            case "profile create" -> {
                String key=slug(require(role,"--role"));profiles.putIfAbsent(key,"ACTIVE");save(new State(profiles,bindings));out.println(key+" "+profiles.get(key));
            }
            case "profile show","profile validate","profile descriptor show","profile descriptor validate","profile resume history" -> {
                String key=require(profile,"--profile");
                if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                if(op.startsWith("profile descriptor"))out.println("{\"synthetic\":true,\"professionalSummary\":\"Example applicant\",\"skills\":[\"Java\"],\"employmentHistory\":[],\"education\":[]}");
                else if(op.equals("profile resume history"))out.println("Synthetic resume fixture (no PDF uploaded)");
                else out.println(key+" "+profiles.get(key));
            }
            case "profile activate","profile deactivate" -> {
                String key=require(profile,"--profile");if(!profiles.containsKey(key))throw new IllegalArgumentException("Unknown profile");
                profiles.put(key,op.equals("profile activate")?"ACTIVE":"INACTIVE");save(new State(profiles,bindings));
            }
            default -> {out.println("Not supported in sandbox: "+op);return 3;}
        }
        return 0;
    }
}

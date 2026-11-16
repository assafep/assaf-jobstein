package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.environment.EnvironmentService;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import io.github.assafep.jobstein.storage.*;
import io.github.assafep.jobstein.sandbox.*;
import io.github.assafep.jobstein.domain.ApplicantProfile.Status;
import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.provisioning.*;
import io.github.assafep.jobstein.validation.DoctorService;

final class SandboxService {
    private final EnvironmentService environments;
    private final ConfigurationRepository repository;
    private final PrintWriter out;
    private final Path workspaceRoot;
    SandboxService(EnvironmentService environments, PrintWriter out) {
        this.environments=environments; this.out=out;
        this.repository=new ConfigurationRepository(environments.directory("sandbox").resolve("simulation"));
        this.workspaceRoot=environments.directory("sandbox").resolve("workspace-v2");
    }
    private void requireFixture(String name)throws IOException {
        if(repository.load(name,Map.class).isEmpty())throw new IOException("Run sandbox onboarding to initialize fixtures");
    }
    private UserProfileService users()throws IOException{
        return new UserProfileService(new ConfigurationRepository(workspaceRoot.resolve("config")),
            new LocalWorkspaceStorage(workspaceRoot.resolve("drive")),new MockAiExecution(aiConfig()),"sandbox");
    }
    private void initializeProfile(String role,Path supplied)throws Exception{
        var service=users();var state=service.create(role);
        if(supplied!=null){service.upload(state.profile().slug(),supplied);return;}
        if(!state.resumes().isEmpty())return;
        Path file=SyntheticResume.create(workspaceRoot.resolve("fixtures"),state.profile().slug(),1);
        service.upload(state.profile().slug(),file);
        service.generate(state.profile().slug(),file);
        service.activate(state.profile().slug(),true);
    }
    private Path fixture(String profile,int version)throws IOException{
        return SyntheticResume.create(workspaceRoot.resolve("fixtures"),profile,version);
    }
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
    int execute(String op,String role,String profile,String agent,String integration,Map<String,String> fields,boolean makeDefault,Path resume,boolean approveDescriptor)throws Exception{
        out.println("SANDBOX — simulated data; no external API calls");
        if(op.equals("onboard")){
            environments.create("sandbox");
            var workflow=new OnboardingService(new ProvisioningStateRepository(new ConfigurationRepository(workspaceRoot.resolve("onboarding"))),out::println);
            workflow.run(new OnboardingService.Steps(
                ()->repository.save("mock-google-project",Map.of("projectId","jobstein-sandbox")),
                ()->repository.save("mock-google-auth",Map.of("subject","synthetic-user")),
                ()->saveAi(aiConfig()),
                ()->users().initialize("Example Applicant"),
                ()->initializeProfile("Software Engineer",null)
            ));
            out.println("Simulated GCP, OAuth, AI, Drive, user and first profile initialized");
            return 0;
        }
        var service=users();
        var ai=aiConfig();
        switch(op){
            case "doctor" -> {
                var report=new DoctorService().inspect(List.of(
                    new DoctorService.Probe("Local environment",()->environments.validate("sandbox")),
                    new DoctorService.Probe("SIMULATED Google project",()->requireFixture("mock-google-project")),
                    new DoctorService.Probe("SIMULATED OAuth",()->requireFixture("mock-google-auth")),
                    new DoctorService.Probe("SIMULATED AI integration",()->aiConfig()),
                    new DoctorService.Probe("SIMULATED user and shared storage",service::validateUser),
                    new DoctorService.Probe("Applicant profiles",()->{
                        boolean active=false;
                        for(var entry:service.profiles().entries().values())if(entry.profile().status()==Status.ACTIVE){service.descriptor(entry.profile().slug());active=true;}
                        if(!active)throw new IOException("No active sandbox profile");
                    })
                ));
                report.checks().forEach(c->out.println(c.status()+" "+c.id()+" "+c.message()));
                out.println(report.ready()?"PASS Sandbox fixture ready":"FAIL Sandbox fixture not ready");
                return report.ready()?0:1;
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
            case "user show" -> out.println(service.user());
            case "user initialize" -> out.println(service.initialize("Example Applicant"));
            case "user validate" -> service.validateUser();
            case "profile list" -> new TreeMap<>(service.profiles().entries()).forEach((k,v)->out.println(k+" "+v.profile().status()));
            case "profile create" -> {
                initializeProfile(require(role,"--role"),resume);
                var state=service.get(slug(role));out.println(state.profile().slug()+" "+state.profile().status());
            }
            case "profile show" -> out.println(service.get(require(profile,"--profile")));
            case "profile validate","profile descriptor show","profile descriptor validate" -> out.println(service.descriptor(require(profile,"--profile")).toPrettyString());
            case "profile resume history" -> service.get(require(profile,"--profile")).resumes().forEach(out::println);
            case "profile resume upload","profile resume update" -> {
                String key=require(profile,"--profile");
                int next=service.get(key).resumes().size()+1;
                service.upload(key,resume==null?fixture(key,next):resume);
            }
            case "profile descriptor regenerate" -> {
                String key=require(profile,"--profile");
                var state=service.get(key);
                if(state.resumes().isEmpty())throw new IOException("Upload a resume first");
                Path file=resume==null?fixture(key,state.resumes().size()):resume;
                service.generate(key,file);
                if(approveDescriptor)service.activate(key,true);
                out.println(service.descriptor(key).toPrettyString());
            }
            case "profile activate","profile deactivate" -> {
                if(op.equals("profile activate")&&!approveDescriptor)throw new IllegalArgumentException("Use --approve-descriptor to approve the synthetic descriptor");
                service.activate(require(profile,"--profile"),op.equals("profile activate"));
            }
            default -> {out.println("Not supported in sandbox: "+op);return 3;}
        }
        return 0;
    }
}

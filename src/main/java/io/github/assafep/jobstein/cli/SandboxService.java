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
            new LocalWorkspaceStorage(workspaceRoot.resolve("drive")),aiService(),"sandbox");
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
    private AiService aiService(){
        var definition=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        definition.put("id","mock-xai").put("adapterType","mock-chat");
        definition.putObject("fields").putObject("model").put("type","STRING").put("required",true);
        return new AiService(repository,new ProviderCatalog(Map.of("mock-xai",definition)),
            Map.of("mock-chat",MockAiExecution.providerAdapter()),"mock-ai");
    }
    private AiConfiguration aiConfig()throws IOException{
        return aiService().load();
    }
    private void initializeAi()throws Exception{
        var service=aiService();
        if(service.existing().isEmpty())
            service.put("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok")),true,false);
        service.validate();
    }
    private static AiIntegrationConfig selected(AiConfiguration config,String integration){
        var result=config.integrations().get(integration==null?config.defaultIntegration():integration);
        if(result==null)throw new IllegalArgumentException("Unknown integration");
        return result;
    }
    private void changeAi(String op,String integration,Map<String,String> fields,boolean makeDefault)throws Exception {
        var service=aiService();
        var config=service.load();
        String id=integration==null&&op.equals("ai configure")?config.defaultIntegration():require(integration,"--integration");
        if(op.equals("ai integration remove")){service.remove(id);return;}
        boolean exists=config.integrations().containsKey(id);
        var values=new HashMap<String,String>();
        if(exists)values.putAll(config.integrations().get(id).configuration());
        values.putIfAbsent("model","mock-grok");
        values.putAll(fields);
        boolean update=op.equals("ai integration update")||(op.equals("ai configure")&&exists);
        service.put(id,new AiIntegrationConfig("mock-xai",values),makeDefault||op.equals("ai configure"),update);
    }
    private static String require(String value,String label){
        if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");
        return value;
    }
    private static String slug(String role){return io.github.assafep.jobstein.storage.UserProfileService.slug(role);}
    int execute(String op,String role,String profile,String agent,String integration,Map<String,String> fields,boolean makeDefault,Path resume,boolean approveDescriptor,String simulateFailure,String reconciliationSource,boolean applyReconciliation)throws Exception{
        out.println("SANDBOX — simulated data; no external API calls");
        if(op.equals("onboard")){
            var failure=SandboxFailure.parse(simulateFailure);
            environments.create("sandbox");
            var workflow=new OnboardingService(new ProvisioningStateRepository(new ConfigurationRepository(workspaceRoot.resolve("onboarding"))),out::println);
            workflow.run(new OnboardingService.Steps(
                failure.wrap("google-configure",()->repository.save("mock-google-project",Map.of("projectId","jobstein-sandbox")),out::println),
                failure.wrap("google-authorize",()->repository.save("mock-google-auth",Map.of("subject","synthetic-user")),out::println),
                failure.wrap("ai-configure",this::initializeAi,out::println),
                failure.wrap("user-initialize",()->users().initialize("Example Applicant"),out::println),
                failure.wrap("first-profile",()->initializeProfile("Software Engineer",null),out::println)
            ));
            out.println("Simulated GCP, OAuth, AI, Drive, user and first profile initialized");
            return execute("doctor",null,null,null,null,Map.of(),false,null,false,null,null,false);
        }
        var service=users();
        if(op.startsWith("state "))return StateCommands.execute(op,service.recovery(),reconciliationSource,applyReconciliation,out);
        switch(op){
            case "doctor" -> {
                var report=new DoctorService().inspect(List.of(
                    new DoctorService.Probe("Local environment",()->environments.validate("sandbox")),
                    new DoctorService.Probe("SIMULATED Google project",()->requireFixture("mock-google-project")),
                    new DoctorService.Probe("SIMULATED OAuth",()->requireFixture("mock-google-auth")),
                    new DoctorService.Probe("SIMULATED AI integration",()->aiService().validate()),
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
            case "ai provider list" -> aiService().catalog().ids().stream().sorted().forEach(out::println);
            case "ai provider models" -> {var c=selected(aiConfig(),integration);aiService().adapter(c).models(c).forEach(out::println);}
            case "ai integration list" -> new TreeMap<>(aiConfig().integrations()).forEach((id,value)->out.println(id+" "+value.provider()+" "+value.configuration().get("model")));
            case "ai integration show" -> out.println(selected(aiConfig(),integration));
            case "ai integration validate" -> {var c=selected(aiConfig(),integration);aiService().catalog().validate(c);aiService().adapter(c).validate(c);out.println("Simulated integration valid");}
            case "ai validate" -> {aiService().validate();out.println("Simulated configuration valid");}
            case "ai configure","ai integration create","ai integration update","ai integration remove" -> changeAi(op,integration,fields,makeDefault);
            case "ai agent list" -> {var ai=aiConfig();out.println("Default: "+ai.defaultIntegration());new TreeMap<>(ai.agentBindings()).forEach((a,i)->out.println(a+" -> "+i));}
            case "ai agent show" -> out.println(new AiIntegrationResolver(aiConfig()).resolve(require(agent,"--agent")).id());
            case "ai agent configure" -> aiService().bind(require(agent,"--agent"),require(integration,"--integration"));
            case "ai agent reset" -> aiService().bind(require(agent,"--agent"),null);
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

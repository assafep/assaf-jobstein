package io.github.assafep.jobstein.cli;

import picocli.CommandLine;
import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.config.*;
import io.github.assafep.jobstein.environment.*;
import io.github.assafep.jobstein.google.*;
import io.github.assafep.jobstein.security.*;
import io.github.assafep.jobstein.storage.*;
import io.github.assafep.jobstein.provisioning.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.nio.charset.StandardCharsets;

@CommandLine.Command(mixinStandardHelpOptions=true)
final class AdminCommand implements Callable<Integer> {
    private final String operation;
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    @CommandLine.Option(names="--env",defaultValue="production") String env;
    @CommandLine.Option(names="--project") String project;
    @CommandLine.Option(names="--create-project") boolean createProject;
    @CommandLine.Option(names="--oauth-client") Path oauthClient;
    @CommandLine.Option(names="--provider",defaultValue="xai") String provider;
    @CommandLine.Option(names="--integration") String integration;
    @CommandLine.Option(names="--agent") String agent;
    @CommandLine.Option(names="--name") String name;
    @CommandLine.Option(names="--role") String role;
    @CommandLine.Option(names="--profile") String profile;
    @CommandLine.Option(names="--resume") Path resume;
    @CommandLine.Option(names="--field",description="Provider field, e.g. model=MODEL") Map<String,String> fields=new HashMap<>();
    @CommandLine.Option(names="--secret-ref",description="Existing secret reference by field name") Map<String,String> references=new HashMap<>();
    @CommandLine.Option(names="--default") boolean makeDefault;
    @CommandLine.Option(names="--allow-ai-processing") boolean allowAi;
    @CommandLine.Option(names="--approve-descriptor") boolean approveDescriptor;
    @CommandLine.Option(names="--non-interactive") boolean nonInteractive;
    @CommandLine.Option(names="--simulate-failure",description="Sandbox-only onboarding checkpoint failure") String simulateFailure;
    private ConfigurationRepository config;
    private SecretStore secrets;
    private EnvironmentService environments;
    private Path directory;
    private PrintWriter out(){return spec.commandLine().getOut();}
    private String ask(String label,String value,String fallback){
        if(value!=null&&!value.isBlank())return value;
        if(nonInteractive){if(fallback!=null)return fallback;throw new IllegalArgumentException("Missing "+label);}
        Console console=System.console();
        if(console==null){if(fallback!=null)return fallback;throw new IllegalArgumentException("Terminal required for "+label);}
        String response=console.readLine("%s%s: ",label,fallback==null?"":" ["+fallback+"]");
        if(response==null)throw new IllegalArgumentException("Input cancelled");
        if(response.isBlank()&&fallback!=null)return fallback;
        if(response.isBlank())throw new IllegalArgumentException("Missing "+label);
        return response.trim();
    }
    private boolean confirm(String label,boolean approved){
        if(approved)return true;
        if(nonInteractive||System.console()==null)return false;
        return "y".equalsIgnoreCase(System.console().readLine("%s [y/N]: ",label));
    }
    AdminCommand(String operation){this.operation=operation;}
    private GoogleOAuth oauth(){return new GoogleOAuth(secrets);}
    private AiService ai()throws Exception{return new AiService(config,secrets);}
    private UserProfileService users()throws Exception{return new UserProfileService(config,new WorkspaceClient(oauth()),ai(),env);}
    private GoogleSettings google()throws IOException{return config.load("google",GoogleSettings.class).orElseThrow(()->new IOException("Google not configured"));}
    private void browser(String url){
        out().println("Open in browser: "+url);out().flush();
        try{if(java.awt.Desktop.isDesktopSupported()&&java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));}catch(Exception ignored){}
    }
    private void configureGoogle()throws Exception{
        var previous=config.load("google",GoogleSettings.class);
        String id=ask("GCP project ID",project,previous.map(GoogleSettings::projectId).orElse(null));
        if(previous.isPresent()&&!previous.get().projectId().equals(id))throw new IOException("Environment already bound to another project");
        var gcloud=new GcloudClient();
        gcloud.configure(id,createProject||confirm("Create GCP project if missing",false));
        config.save("google",new GoogleSettings(id));
        if(oauthClient!=null)oauth().importClient(oauthClient,id);
        if(secrets.get("secret://google/client").isEmpty()){
            out().println("Configure Google Auth Platform branding, External audience, publishing status, scopes and Desktop client.");
            browser("https://console.cloud.google.com/auth/clients?project="+id);
            oauth().importClient(Path.of(ask("Downloaded desktop OAuth JSON",null,null)),id);
        }
    }
    private void authorize()throws Exception{
        if(oauthClient!=null)oauth().importClient(oauthClient,google().projectId());
        var expected=config.load("user",io.github.assafep.jobstein.domain.JobsteinUser.class).map(io.github.assafep.jobstein.domain.JobsteinUser::googleAccountSubject);
        oauth().authorize(this::browser,expected);
        new WorkspaceClient(oauth()).validate();
    }
    private void configureAi(boolean update,boolean defaultBinding)throws Exception{
        var service=ai();
        String id=ask("Integration name",integration,defaultBinding?"standard":null);
        var old=service.existing().map(AiConfiguration::integrations).orElse(Map.of()).get(id);
        String selected=update&&old!=null?old.provider():provider;
        var definition=service.catalog().definition(selected);
        var values=new HashMap<String,String>();
        if(update&&old!=null)values.putAll(old.configuration());
        var iterator=definition.path("fields").fields();
        while(iterator.hasNext()){
            var entry=iterator.next();String key=entry.getKey();var field=entry.getValue();
            if("SECRET".equals(field.path("type").asText())){
                String ref=references.getOrDefault(key,values.get(key));
                if(ref==null){
                    ref="secret://ai/"+selected+"/"+id+"/"+key;
                    if(nonInteractive||System.console()==null)throw new IOException("Use --secret-ref for existing credentials or an interactive terminal");
                    char[] secret=System.console().readPassword("%s: ",field.path("displayName").asText(key));
                    if(secret==null||secret.length==0)throw new IOException("Secret required");
                    try{secrets.put(ref,new String(secret).getBytes(StandardCharsets.UTF_8));}finally{Arrays.fill(secret,'\0');}
                }
                values.put(key,ref);
            }else{
                String value=fields.getOrDefault(key,values.get(key));
                if(value==null&&(field.path("required").asBoolean()||!nonInteractive)){
                    if("model".equals(key)){
                        var preview=new AiIntegrationConfig(selected,values);
                        out().println("Available models: "+service.adapter(preview).models(preview));
                    }
                    if(field.path("required").asBoolean())value=ask(field.path("displayName").asText(key),null,null);
                    else if(System.console()!=null)value=System.console().readLine("%s (optional): ",field.path("displayName").asText(key));
                }
                if(value!=null&&!value.isBlank())values.put(key,value);
            }
        }
        service.put(id,new AiIntegrationConfig(selected,values),defaultBinding||makeDefault,update);
    }
    private String selectedProfile(){return ask("Profile slug",profile,null);}
    private Path selectedResume(){return Path.of(ask("Resume PDF",resume==null?null:resume.toString(),null));}
    private void generate(String slug,Path file)throws Exception{
        if(!confirm("Allow configured AI provider to process resume contents",allowAi))throw new IOException("AI processing not authorized");
        var service=users();service.generate(slug,file);
        out().println(service.descriptor(slug).toPrettyString());
        if(confirm("Approve descriptor and activate profile",approveDescriptor))service.activate(slug,true);
    }
    private void createProfile()throws Exception{
        var service=users();var state=service.create(ask("Target role",role,null));
        String slug=state.profile().slug();Path file=selectedResume();
        state=service.upload(slug,file);
        if(state.descriptorId()==null)generate(slug,file);
        else if(state.profile().status()!=io.github.assafep.jobstein.domain.ApplicantProfile.Status.ACTIVE){
            out().println(service.descriptor(slug).toPrettyString());
            if(confirm("Approve descriptor and activate profile",approveDescriptor))service.activate(slug,true);
        }
    }
    private boolean doctor()throws Exception{
        var doctor=new io.github.assafep.jobstein.validation.DoctorService();
        var report=doctor.inspect(List.of(
            new io.github.assafep.jobstein.validation.DoctorService.Probe("Local environment",()->environments.validate(env)),
            new io.github.assafep.jobstein.validation.DoctorService.Probe("GCP project and enabled APIs",()->new GcloudClient().verify(google().projectId())),
            new io.github.assafep.jobstein.validation.DoctorService.Probe("Google OAuth and Workspace read access",()->new WorkspaceClient(oauth()).validate()),
            new io.github.assafep.jobstein.validation.DoctorService.Probe("AI integrations and model availability",()->ai().validate()),
            new io.github.assafep.jobstein.validation.DoctorService.Probe("User identity and shared Drive state",()->users().validateUser()),
            new io.github.assafep.jobstein.validation.DoctorService.Probe("Applicant profiles",()->{
                var service=users();boolean active=false;
                for(var state:service.profiles().entries().values())if(state.profile().status()==io.github.assafep.jobstein.domain.ApplicantProfile.Status.ACTIVE){service.descriptor(state.profile().slug());active=true;}
                if(!active)throw new IOException("No active profile");
            })
        ));
        report.checks().forEach(c->out().println(c.status()+" "+c.id()+" "+c.message()));
        out().println(report.ready()?"Configured checks passed (no email sent)":"Environment not ready");
        return report.ready();
    }
    private int onboard()throws Exception{
        environments.create(env);
        var workflow=new OnboardingService(new ProvisioningStateRepository(config),out()::println);
        workflow.run(new OnboardingService.Steps(
            this::configureGoogle,this::authorize,
            ()->{if(ai().existing().isEmpty())configureAi(false,true);ai().validate();},
            ()->{var service=users();String existing=config.load("user",io.github.assafep.jobstein.domain.JobsteinUser.class).map(io.github.assafep.jobstein.domain.JobsteinUser::fullName).orElse(null);service.initialize(ask("Full name",name,existing));},
            ()->{createProfile();if(users().profiles().entries().values().stream().noneMatch(p->p.profile().status()==io.github.assafep.jobstein.domain.ApplicantProfile.Status.ACTIVE))throw new IOException("Approve an applicant descriptor to continue");}
        ));
        return doctor()?0:1;
    }
    public Integer call(){
        try{
            environments=new EnvironmentService(Path.of(System.getProperty("user.home"),".config","jobstein","environments"));
            if(simulateFailure!=null&&(!"sandbox".equals(env)||!operation.equals("onboard")))throw new IllegalArgumentException("Failure injection is only available for sandbox onboarding");
            try(var ignored=io.github.assafep.jobstein.environment.EnvironmentLock.acquire(environments.directory(env))){
            io.github.assafep.jobstein.security.PrivateState.directory(environments.directory(env));
            if("sandbox".equals(env))return new SandboxService(environments,out()).execute(operation,role,profile,agent,integration,fields,makeDefault,resume,approveDescriptor,simulateFailure);
            directory=environments.directory(env);
            if(!operation.equals("onboard"))environments.validate(env);
            config=new ConfigurationRepository(directory);
            secrets=new PosixFileSecretStore(directory.resolve("secrets"));
            switch(operation){
                case "onboard":return onboard();
                case "doctor":return doctor()?0:1;
                case "google configure":configureGoogle();break;
                case "google authorize":authorize();break;
                case "google status":out().println(google().projectId());break;
                case "google validate":new GcloudClient().verify(google().projectId());new WorkspaceClient(oauth()).validate();break;
                case "ai configure":configureAi(ai().existing().map(c->c.integrations().containsKey(integration==null?"standard":integration)).orElse(false),true);break;
                case "ai validate":ai().validate();break;
                case "ai provider list":ai().catalog().ids().stream().sorted().forEach(out()::println);break;
                case "ai provider models":{var service=ai();var cfg=service.load();var selected=cfg.integrations().get(integration==null?cfg.defaultIntegration():integration);service.adapter(selected).models(selected).forEach(out()::println);break;}
                case "ai integration create":configureAi(false,false);break;
                case "ai integration update":configureAi(true,false);break;
                case "ai integration list":ai().load().integrations().keySet().stream().sorted().forEach(out()::println);break;
                case "ai integration show":{var c=ai().load().integrations().get(ask("Integration",integration,null));if(c==null)throw new IOException("Integration missing");out().println(c.provider()+" "+c.configuration().keySet());break;}
                case "ai integration validate":{var service=ai();var c=service.load().integrations().get(ask("Integration",integration,null));if(c==null)throw new IOException("Integration missing");service.catalog().validate(c);service.adapter(c).validate(c);break;}
                case "ai integration remove":ai().remove(ask("Integration",integration,null));break;
                case "ai agent configure":ai().bind(ask("Agent ID",agent,null),ask("Integration",integration,null));break;
                case "ai agent reset":ai().bind(ask("Agent ID",agent,null),null);break;
                case "ai agent list":{var c=ai().load();out().println("Default: "+c.defaultIntegration());c.agentBindings().forEach((a,i)->out().println(a+" -> "+i));break;}
                case "ai agent show":{var r=new AiIntegrationResolver(ai().load()).resolve(ask("Agent ID",agent,null));out().println(r.id()+" ("+r.configuration().provider()+")");break;}
                case "user initialize":users().initialize(ask("Full name",name,null));break;
                case "user show":out().println(users().user());break;
                case "user validate":users().validateUser();break;
                case "profile create":createProfile();break;
                case "profile list":users().profiles().entries().forEach((k,v)->out().println(k+" "+v.profile().status()));break;
                case "profile show":out().println(users().get(selectedProfile()));break;
                case "profile validate":users().descriptor(selectedProfile());break;
                case "profile activate":{String slug=selectedProfile();out().println(users().descriptor(slug).toPrettyString());if(!confirm("Approve descriptor",approveDescriptor))throw new IOException("Approval required");users().activate(slug,true);break;}
                case "profile deactivate":users().activate(selectedProfile(),false);break;
                case "profile resume upload":case "profile resume update":users().upload(selectedProfile(),selectedResume());break;
                case "profile resume history":users().get(selectedProfile()).resumes().forEach(out()::println);break;
                case "profile descriptor show":out().println(users().descriptor(selectedProfile()).toPrettyString());break;
                case "profile descriptor regenerate":generate(selectedProfile(),selectedResume());break;
                case "profile descriptor validate":users().descriptor(selectedProfile());break;
                default:throw new IllegalStateException("Unknown operation");
            }
            return 0;
            }
        }catch(io.github.assafep.jobstein.environment.EnvironmentLock.BusyException e){spec.commandLine().getErr().println(e.getMessage());return 4;}
        catch(InterruptedException e){Thread.currentThread().interrupt();spec.commandLine().getErr().println("Interrupted");return 1;}
        catch(Exception e){spec.commandLine().getErr().println("Operation failed: "+e.getClass().getSimpleName());return 1;}
    }
}

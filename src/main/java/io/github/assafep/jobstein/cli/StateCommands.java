package io.github.assafep.jobstein.cli;

import io.github.assafep.jobstein.storage.ProfileIndexRecovery;
import java.io.PrintWriter;
import java.util.Locale;

final class StateCommands {
    private StateCommands(){}
    static int execute(String operation,ProfileIndexRecovery recovery,String source,boolean apply,PrintWriter out)throws Exception{
        if(!operation.equals("state reconcile")&&(apply||source!=null))throw new IllegalArgumentException("Reconciliation options require state reconcile");
        switch(operation){
            case "state status" -> out.println(recovery.status());
            case "state validate" -> out.println(recovery.validate());
            case "state backup" -> out.println("Profile index backup: "+recovery.backup());
            case "state reconcile" -> {
                if(source==null)throw new IllegalArgumentException("Select local or remote source");
                var result=recovery.reconcile(ProfileIndexRecovery.Source.valueOf(source.toUpperCase(Locale.ROOT)),apply);
                out.println(result.before());
                result.backup().ifPresent(name->out.println("Profile index backup: "+name));
                out.println(result.applied()?"Reconciliation applied":apply?"No changes required":"Dry run: no changes made");
            }
            default -> throw new IllegalArgumentException("Unknown state command");
        }
        return 0;
    }
}

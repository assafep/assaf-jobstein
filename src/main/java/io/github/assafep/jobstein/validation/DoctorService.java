package io.github.assafep.jobstein.validation;

import java.util.*;
import io.github.assafep.jobstein.validation.ValidationResult.*;

public final class DoctorService {
    @FunctionalInterface public interface Action {void run()throws Exception;}
    public record Probe(String id,Action action){
        public Probe{Objects.requireNonNull(id);Objects.requireNonNull(action);}
    }
    public ValidationResult inspect(List<Probe> probes)throws InterruptedException{
        var checks=new ArrayList<Check>();
        for(var probe:probes){
            try{probe.action().run();checks.add(new Check(probe.id(),Status.PASS,""));}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
            catch(Exception e){checks.add(new Check(probe.id(),Status.FAIL,Diagnostics.describe(e)));}
        }
        return new ValidationResult(checks);
    }
}

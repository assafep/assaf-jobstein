package io.github.assafep.jobstein.environment;

import java.nio.file.Path;

/** Child JVM entry point for cross-process lock tests. */
public final class EnvironmentLockProbe {
    public static void main(String[] args)throws Exception{
        try(var lock=EnvironmentLock.acquire(Path.of(args[0]))){
            System.out.println("ACQUIRED");
            System.out.flush();
            if(args.length>1&&args[1].equals("hold"))System.in.read();
        }catch(EnvironmentLock.BusyException busy){System.exit(4);}
    }
}

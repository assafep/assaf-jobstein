package io.github.assafep.jobstein.environment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentLockTest {
    @TempDir Path root;
    @Test void rejectsCompetingAcquisitionAndReleasesOnClose()throws Exception{
        Path production=root.resolve("production");
        try(var first=EnvironmentLock.acquire(production)){
            assertThrows(EnvironmentLock.BusyException.class,()->EnvironmentLock.acquire(production));
            try(var other=EnvironmentLock.acquire(root.resolve("sandbox"))){assertNotNull(other);}
        }
        try(var next=EnvironmentLock.acquire(production)){assertNotNull(next);}
    }
    @Test void cliFailsClosedWhenEnvironmentIsLocked()throws Exception{
        String old=System.getProperty("user.home");
        try{
            System.setProperty("user.home",root.toString());
            Path dir=root.resolve(".config/jobstein/environments/sandbox");
            try(var lock=EnvironmentLock.acquire(dir)){
                assertEquals(4,io.github.assafep.jobstein.cli.Jobstein.commandLine().execute("onboard","--env","sandbox","--non-interactive"));
                assertEquals(4,io.github.assafep.jobstein.cli.Jobstein.commandLine().execute("environment","create","--env","sandbox"));
            }
        }finally{System.setProperty("user.home",old);}
    }
    private Process child(Path directory,boolean hold)throws Exception{
        String separator=java.io.File.pathSeparator;
        String classpath=Path.of(EnvironmentLockProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            +separator+Path.of(EnvironmentLock.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var command=new java.util.ArrayList<>(java.util.List.of(
            Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,
            EnvironmentLockProbe.class.getName(),directory.toString()));
        if(hold)command.add("hold");
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
    private int exit(Process process)throws Exception{
        try{
            assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS),"Child JVM did not exit");
            return process.exitValue();
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    @Test void rejectedInProcessAttemptDoesNotReleaseOperatingSystemLock()throws Exception{
        Path directory=root.resolve("development");
        try(var first=EnvironmentLock.acquire(directory)){
            assertThrows(EnvironmentLock.BusyException.class,()->EnvironmentLock.acquire(directory.resolve(".")));
            assertEquals(4,exit(child(directory,false)));
            assertEquals(0,exit(child(root.resolve("sandbox"),false)));
        }
        assertEquals(0,exit(child(directory,false)));
    }
    @Test void childProcessOwnsLockUntilExit()throws Exception{
        Path directory=root.resolve("development");
        Process process=child(directory,true);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            try{
                var ready=executor.submit(()->new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream())).readLine());
                assertEquals("ACQUIRED",ready.get(15,java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(EnvironmentLock.BusyException.class,()->EnvironmentLock.acquire(directory));
                process.getOutputStream().write(1);
                process.getOutputStream().flush();
                assertEquals(0,exit(process));
                try(var lock=EnvironmentLock.acquire(directory)){assertNotNull(lock);}
            }finally{if(process.isAlive())process.destroyForcibly();}
        }
    }
}

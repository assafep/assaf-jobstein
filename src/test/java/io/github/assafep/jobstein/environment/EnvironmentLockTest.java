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
}

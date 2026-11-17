package io.github.assafep.jobstein.security;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LinuxSecretServiceStoreTest {
    @TempDir Path root;
    @Test void usesStdinForValuesAndIsolatesEnvironments()throws Exception{
        var entries=new HashMap<List<String>,byte[]>();
        LinuxSecretServiceStore.Runner runner=(args,input)->{
            var key=List.copyOf(args.subList(args.indexOf("application"),args.size()));
            return switch(args.getFirst()){
                case "store" -> {entries.put(key,input.clone());yield new LinuxSecretServiceStore.Result(0,new byte[0]);}
                case "lookup" -> new LinuxSecretServiceStore.Result(entries.containsKey(key)?0:1,entries.getOrDefault(key,new byte[0]).clone());
                case "clear" -> {entries.remove(key);yield new LinuxSecretServiceStore.Result(0,new byte[0]);}
                default -> throw new AssertionError();
            };
        };
        var one=new LinuxSecretServiceStore(root.resolve("one"),runner);
        var two=new LinuxSecretServiceStore(root.resolve("two"),runner);
        byte[] secret={1,2,3,0,127};
        one.put("secret://google/client",secret);
        assertArrayEquals(secret,one.get("secret://google/client").orElseThrow());
        assertThrows(IOException.class,()->two.get("secret://google/client"));
        one.remove("secret://google/client");
        assertThrows(IOException.class,()->one.get("secret://google/client"));
    }
}

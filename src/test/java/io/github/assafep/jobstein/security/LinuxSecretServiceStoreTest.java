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
    @Test void lookupClearsOutputOnSuccessInvalidEncodingAndBackendFailure()throws Exception{
        for(int exit:new int[]{0,1}){
            for(String payload:new String[]{" \tAQID\r\n", "invalid!"}){
                byte[] output=payload.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                var store=new LinuxSecretServiceStore(root,(args,input)->new LinuxSecretServiceStore.Result(exit,output));
                if(exit==0 && payload.startsWith(" ")){
                    assertArrayEquals(new byte[]{1,2,3},store.get("secret://synthetic/token").orElseThrow());
                }else{
                    IOException failure=assertThrows(IOException.class,()->store.get("secret://synthetic/token"));
                    assertFalse(failure.getMessage().contains(payload));
                    assertNull(failure.getCause());
                }
                assertArrayEquals(new byte[output.length],output);
            }
        }
    }

    @Test void mutationsClearCommandOutputAndEncodedInputEvenWhenTheyFail()throws Exception{
        for(String operation:List.of("store","clear")){
            for(int exit:new int[]{0,1}){
                var outputs=new ArrayList<byte[]>();
                var inputs=new ArrayList<byte[]>();
                var store=new LinuxSecretServiceStore(root,(args,input)->{
                    inputs.add(input);
                    byte[] output=new byte[]{65,81,73,68}; // synthetic Base64 AQID
                    outputs.add(output);
                    return new LinuxSecretServiceStore.Result(args.getFirst().equals("lookup")?0:exit,output);
                });
                byte[] original={1,2,3};
                org.junit.jupiter.api.function.Executable action=()->{
                    if(operation.equals("store"))store.put("secret://synthetic/token",original);
                    else store.remove("secret://synthetic/token");
                };
                if(exit==0)assertDoesNotThrow(action);
                else assertThrows(IOException.class,action);
                for(byte[] output:outputs)assertArrayEquals(new byte[output.length],output);
                for(byte[] input:inputs)assertArrayEquals(new byte[input.length],input);
                assertArrayEquals(new byte[]{1,2,3},original);
            }
        }
    }
}

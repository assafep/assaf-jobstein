package io.github.assafep.jobstein.google;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GoogleAccountBindingTest {
    @Test void rejectsAccountSwitchAndMissingIdentity()throws Exception{
        GoogleAccountBinding.verify(Optional.empty(),"synthetic");
        GoogleAccountBinding.verify(Optional.of("synthetic"),"synthetic");
        assertThrows(IOException.class,()->GoogleAccountBinding.verify(Optional.of("synthetic"),"different"));
        assertThrows(IOException.class,()->GoogleAccountBinding.verify(Optional.empty(),""));
    }
}

package gpu.accounts;

import java.io.*;

// Defines the methods required for storing account data.
// Different classes can implement this interface.
public interface AccountRepository {

    // Returns saved bytes, or null if no saved file exists.
    byte[] load() throws IOException;

    // Saves the supplied account snapshot.
    void save(byte[] snapshot) throws IOException;
}
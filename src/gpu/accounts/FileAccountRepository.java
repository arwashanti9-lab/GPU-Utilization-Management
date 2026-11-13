package gpu.accounts;

import java.io.*;
import java.nio.file.*;

// Implements AccountRepository using a file on the computer.
public final class FileAccountRepository implements AccountRepository {
    private final Path file;

    public FileAccountRepository(Path file) {
        this.file = file.toAbsolutePath();
    }

    @Override
    public byte[] load() throws IOException {
        // A missing file means the application is being used for the first time.
        return Files.exists(file) ? Files.readAllBytes(file) : null;
    }

    @Override
    public void save(byte[] snapshot) throws IOException {
        // Creates the containing folder if it does not exist.
        Files.createDirectories(file.getParent());

        // Writes to a temporary file before replacing the previous saved data.
        Path temporary = Files.createTempFile(
                file.getParent(), "accounts-", ".tmp"
        );

        try {
            Files.write(temporary, snapshot);

            try {
                // Replaces the old file in one operation when supported.
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException exception) {
            	// Uses a normal file replacement if replacing it in one operation is not supported.
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            // Removes any temporary file left after saving.
            Files.deleteIfExists(temporary);
        }
    }
}
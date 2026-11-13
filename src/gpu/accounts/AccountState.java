package gpu.accounts;

import java.io.*;
import java.util.*;

// Groups account information into one object for saving and loading.
final class AccountState implements Serializable {
    private static final long serialVersionUID = 1L;

    // Maps connect each university ID to its corresponding information.
    final Map<String, Student> students = new LinkedHashMap<>();
    final Map<String, AccessRequest> requests = new LinkedHashMap<>();
    final Map<String, TimeAllowance> allowances = new LinkedHashMap<>();

    // Stores the history of allowance changes.
    final List<AllowanceChange> changes = new ArrayList<>();
    
    // Stores each job's student ID, reserved minutes and status.
    final Map<String, String> jobOwners = new LinkedHashMap<>();
    final Map<String, Integer> jobReservations = new LinkedHashMap<>();

    // Records settled jobs to prevent charging the same job twice.
    final Set<String> settledJobs = new HashSet<>();
    
    // Copies the account data so changes do not affect the original.
    AccountState copy() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Converts this object into bytes.
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(this);
        }

        // Converts the bytes into a new, independent object.
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(buffer.toByteArray()))) {

            return (AccountState) input.readObject();

        } catch (ClassNotFoundException exception) {
            throw new IOException("Cannot copy account data.", exception);
        }
    }
}
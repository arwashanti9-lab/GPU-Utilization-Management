package gpu.accounts;

import java.io.*;
import java.util.*;
import java.util.function.*;

//Handles registration, login, approvals and allowances.
public final class AccountService {
    private final AccountRepository repository;

    // Creates the default admin account with username "admin" and password "gpu2026".
    private final Administrator administrator = new Administrator();

    private AccountState state;
    private User currentUser;

    public AccountService(AccountRepository repository) throws IOException {
        this.repository = Objects.requireNonNull(repository);

        // Restore previous data, or begin with empty collections.
        byte[] saved = repository.load();
        state = saved == null ? new AccountState() : decode(saved);
    }

	// Restores accounts, requests and balances from the saved file data.
    private static AccountState decode(byte[] bytes) throws IOException {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {

        	    // Blocks unexpected data types and objects that are too large when loading the file.
            input.setObjectInputFilter(info -> {
                Class<?> type = info.serialClass();

                if (info.depth() > 40
                        || info.references() > 200000
                        || info.arrayLength() > 2000000) {

                    return ObjectInputFilter.Status.REJECTED;
                }

                if (type == null) {
                    return ObjectInputFilter.Status.UNDECIDED;
                }

                // Checks the type of objects stored in the array.
                while (type.isArray()) {
                    type = type.getComponentType();
                }

                String name = type.getName();

                boolean allowed = type.isPrimitive()
                        || name.startsWith("gpu.accounts.")
                        || name.startsWith("java.lang.")
                        || name.startsWith("java.util.");

                return allowed
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.REJECTED;
            });

            Object result = input.readObject();

            if (!(result instanceof AccountState)) {
                throw new IOException("Invalid account file.");
            }

            return (AccountState) result;

        } catch (ClassNotFoundException | RuntimeException exception) {
            throw new IOException("Cannot read account file.", exception);
        }
    }

    // Applies a change to a copy and saves it before updating the live data.
    // If saving fails, the original in-memory data remains unchanged.
    private void change(Consumer<AccountState> action) throws IOException {
        AccountState next = state.copy();
        action.accept(next);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(next);
        }

        repository.save(buffer.toByteArray());
        state = next;
    }

    // Checks that a required text field is not empty.
    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }

        return value.trim();
    }

    // Restricts administrator operations to administrator accounts only.
    private void requireAdmin() {
        if (currentUser == null
                || currentUser.getRole() != Role.ADMINISTRATOR) {

            throw new IllegalStateException(
                    "Administrator login is required."
            );
        }
    }

    // Administrators can access all accounts.
    // Students can access only the account matching their university ID.
    private void requireAccess(String id) {
        if (currentUser == null
                || (currentUser.getRole() != Role.ADMINISTRATOR
                && !currentUser.getUsername().equals(id))) {

            throw new IllegalStateException(
                    "You cannot access this student's records."
            );
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    // Ends the current login session.
    public void logout() {
        currentUser = null;
    }

    public User login(String username, String password) {
        if (currentUser != null) {
            throw new IllegalStateException(
                    "Log out before signing in again."
            );
        }

        String id = required(username, "Username");

        // Only approved students are stored in the students collection.
        User candidate = id.equals("admin")
                ? administrator
                : state.students.get(id);

        if (candidate == null) {
            AccessRequest request = state.requests.get(id);
            if (request != null
                    && request.getStatus() == AccessRequest.Status.PENDING) {
                throw new IllegalArgumentException(
                        "Your registration is pending administrator approval."
                );
            }
            if (request != null
                    && request.getStatus() == AccessRequest.Status.REJECTED) {
                throw new IllegalArgumentException(
                        "Your registration request was rejected."
                );
            }
            throw new IllegalArgumentException(
                    "Invalid username or password."
            );
        }

        if (!candidate.matchesPassword(password)) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        // Remembers who is currently signed in.
        currentUser = candidate;
        return candidate;
    }

    // Checks the registration details and submits the request for admin approval.
    public void register(String universityId, String name, String email,
                         String phone, String password) throws IOException {

        if (currentUser != null) {
            throw new IllegalStateException(
                    "Log out before registering."
            );
        }

        final String id = required(universityId, "University ID");
        final String fullName = required(name, "Name");
        final String mail = required(email, "Email");
        final String telephone = required(phone, "Phone");
        
        // Prevents students from using "admin" as their university ID and limits it to numbers.
        if (!id.matches("[A-Za-z0-9-]+")
                || id.equalsIgnoreCase("admin")) {

            throw new IllegalArgumentException(
                    "Use letters, numbers or hyphens for the university ID."
            );
        }

        // Checks the basic email format.
        if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException(
                    "Enter a valid email address."
            );
        }

        // Allows common phone-number characters.
        if (!telephone.matches("[+0-9() -]{7,25}")) {
            throw new IllegalArgumentException(
                    "Enter a valid phone number."
            );
        }

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        // Prevents duplicate accounts and duplicate registration requests.
        if (state.requests.containsKey(id)
                || state.students.containsKey(id)) {

            throw new IllegalArgumentException(
                    "This university ID already has an account or request."
            );
        }

        Student applicant = new Student(
                id, fullName, mail, telephone, new Credential(password)
        );

        change(next ->
                next.requests.put(id, new AccessRequest(applicant))
        );
    }

    // Returns copies of registration requests for administrator review.
    public List<AccessRequest> getRequests() throws IOException {
        requireAdmin();

        return Collections.unmodifiableList(
                new ArrayList<>(state.copy().requests.values())
        );
    }

    // Returns approved students.
    public List<Student> getStudents() {
        requireAdmin();

        return Collections.unmodifiableList(
                new ArrayList<>(state.students.values())
        );
    }

    // Finds the student's account if the user has permission to view it.
    public Student findStudent(String id) {
        requireAccess(id);

        Student student = state.students.get(id);

        if (student == null) {
            throw new IllegalArgumentException(
                    "Approved student not found."
            );
        }

        return student;
    }

    // Approves a student with the default allowance of 100 minutes.
    public void approve(String id) throws IOException {
        approve(id, 100);
    }

    // Overloaded method allowing a custom initial allowance.
    public void approve(String id, int minutes) throws IOException {
        requireAdmin();

        if (minutes < 0) {
            throw new IllegalArgumentException(
                    "Allocation must be zero or greater."
            );
        }

        change(next -> {
            AccessRequest request = next.requests.get(id);

            if (request == null) {
                throw new IllegalArgumentException("Request not found.");
            }

            // Prevents the same request from being approved twice.
            request.decide(AccessRequest.Status.APPROVED);

            if (next.students.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Account already exists."
                );
            }

            // Activates the account and creates its allowance.
            next.students.put(id, request.getApplicant());
            next.allowances.put(id, new TimeAllowance(minutes));

            audit(next, id, "Initial approval", 0, minutes);
        });
    }
    
    // Rejects the registration request, leaving the student unable to log in.
    public void reject(String id) throws IOException {
        requireAdmin();

        change(next -> {
            AccessRequest request = next.requests.get(id);

            if (request == null) {
                throw new IllegalArgumentException("Request not found.");
            }

            request.decide(AccessRequest.Status.REJECTED);
        });
    }

    public TimeAllowance getAllowance(String id) throws IOException {
        findStudent(id);
        return state.copy().allowances.get(id);
    }
    
    // Sets the student's total allowance to the given amount and saves the admin's reason.
    public void setAllocation(String id, int minutes,
                              String reason) throws IOException {

        requireAdmin();
        findStudent(id);

        String note = required(reason, "Reason");

        change(next -> {
            TimeAllowance allowance = next.allowances.get(id);
            int before = allowance.getAllocated();

            allowance.setAllocated(minutes);

            audit(next, id, note, before, allowance.getAllocated());
        });
    }
    
    // Increases the student's allowance by the given number of minutes.
    public void grantExtraTime(String id, int minutes,
                               String reason) throws IOException {

        requireAdmin();
        findStudent(id);

        String note = required(reason, "Reason");

        change(next -> {
            TimeAllowance allowance = next.allowances.get(id);
            int before = allowance.getAllocated();

            allowance.grant(minutes);

            audit(next, id, note, before, allowance.getAllocated());
        });
    }
    
    // Records the allowance change with a number to identify it.
    private void audit(AccountState next, String id, String reason,
                       int before, int after) {

        next.changes.add(new AllowanceChange(
                next.changes.size() + 1L,
                id,
                currentUser.getUsername(),
                reason,
                before,
                after
        ));
    }
    
    // Returns a list of the changes made to this student's GPU allowance.
    public List<AllowanceChange> getChanges(String id) {
        findStudent(id);

        List<AllowanceChange> result = new ArrayList<>();

        for (AllowanceChange record : state.changes) {
            if (record.getStudentId().equals(id)) {
                result.add(record);
            }
        }

        return Collections.unmodifiableList(result);
    }
    
    // Reserves the required minutes after the job details and task size are checked.
    public void reserveForJob(String studentId, String jobId,
                              int duration) throws IOException {

        findStudent(studentId);
        String key = required(jobId, "Job ID");

        change(next -> {
            // Prevents the same job from reserving minutes more than once.
            if (next.jobOwners.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Job ID already has an allowance record."
                );
            }

            next.allowances.get(studentId).reserve(duration);

            // Saves the student's ID and reserved minutes for updating the balance.
            next.jobOwners.put(key, studentId);
            next.jobReservations.put(key, duration);
        });
    }
    
    // If the job is finished, charge its full runtime.
    // If the job is cancelled while waiting, charge no minutes.
    // If the job is cancelled while running, charge only the minutes used.
    public void settleForJob(String jobId, int charge) throws IOException {
        String owner = state.jobOwners.get(jobId);

        if (owner == null) {
            throw new IllegalArgumentException(
                    "Job allowance record not found."
            );
        }

        requireAccess(owner);

        change(next -> {
            // Prevents a completed/cancelled job from being charged again.
            if (next.settledJobs.contains(jobId)) {
                throw new IllegalArgumentException(
                        "This job has already been settled."
                );
            }

            int reservation = next.jobReservations.get(jobId);

            next.allowances.get(owner).settle(reservation, charge);
            next.settledJobs.add(jobId);
        });
    }

}

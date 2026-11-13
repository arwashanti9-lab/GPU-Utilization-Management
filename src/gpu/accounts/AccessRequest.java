package gpu.accounts;

import java.io.*;

// Represents a student's registration request.
public final class AccessRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    // A request has one of these three states.
    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private final Student applicant;

    // Every new request starts as pending.
    private Status status = Status.PENDING;

    AccessRequest(Student applicant) {
        this.applicant = applicant;
    }

    public Student getApplicant() {
        return applicant;
    }

    public Status getStatus() {
        return status;
    }

    // Changes a pending request to approved or rejected.
    void decide(Status decision) {
        // Prevents repeated approval, repeated rejection or resetting to pending.
        if (status != Status.PENDING || decision == Status.PENDING) {
            throw new IllegalArgumentException(
                    "Only pending requests can be decided once."
            );
        }

        status = decision;
    }
}
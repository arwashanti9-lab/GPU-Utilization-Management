package gpu.accounts;

import java.io.*;

// Keeps a record of an administrator's allowance change.
public final class AllowanceChange implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long sequence;
    private final String studentId;
    private final String administrator;
    private final String reason;
    private final int before;
    private final int after;

    // Initializes all information about one allowance change.
    AllowanceChange(long sequence, String studentId,
                    String administrator, String reason,
                    int before, int after) {

        this.sequence = sequence;
        this.studentId = studentId;
        this.administrator = administrator;
        this.reason = reason;
        this.before = before;
        this.after = after;
    }
    
    //Getter methods.
    public long getSequence() {
        return sequence;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getAdministrator() {
        return administrator;
    }

    public String getReason() {
        return reason;
    }

    public int getBefore() {
        return before;
    }

    public int getAfter() {
        return after;
    }

    // Returns a readable description for the history screen.
    @Override
    public String toString() {
        return "#" + sequence
                + " | " + studentId
                + " | " + before + " -> " + after
                + " minutes | " + administrator
                + " | " + reason;
    }
}
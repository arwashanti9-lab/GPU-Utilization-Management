package gpu.scheduling;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A job record. The scheduler owns changes to its execution state. */
public final class Job implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { QUEUED, RUNNING, COMPLETED, CANCELLED }

    // Package access allows Scheduler to update execution state.
    final String id;
    final String studentId;
    final String title;
    final String application;
    final String description;
    final int duration;
    final LocalDateTime submissionTime;

    Status status;
    int gpuId; // 0 means no GPU has been assigned.
    LocalDateTime started;
    LocalDateTime finished;
    int charged;
    LocalDateTime cancelledAt;
    String cancelledBy;
    String cancellationReason;

    public Job(String id, String studentId, String title, String application,
               String description, int duration, LocalDateTime submissionTime) {
        this.id = required(id, "Job ID");
        this.studentId = required(studentId, "Student ID");
        this.title = required(title, "Title");
        this.application = required(application, "Application name");
        this.description = required(description, "Description");
        if (duration != 5 && duration != 15 && duration != 30 && duration != 60) {
            throw new IllegalArgumentException("Duration must be 5, 15, 30, or 60 minutes.");
        }
        this.duration = duration;
        if (submissionTime == null) {
            throw new IllegalArgumentException("Submission time is required.");
        }
        this.submissionTime = submissionTime;
        this.status = Status.QUEUED;
    }

    /** Creates an independent snapshot of a job for display and history. */
    public Job(Job other) {
        if (other == null) {
            throw new IllegalArgumentException("Job is required.");
        }
        this.id = other.id;
        this.studentId = other.studentId;
        this.title = other.title;
        this.application = other.application;
        this.description = other.description;
        this.duration = other.duration;
        this.submissionTime = other.submissionTime;
        this.status = other.status;
        this.gpuId = other.gpuId;
        this.started = other.started;
        this.finished = other.finished;
        this.charged = other.charged;
        this.cancelledAt = other.cancelledAt;
        this.cancelledBy = other.cancelledBy;
        this.cancellationReason = other.cancellationReason;
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getApplication() { return application; }
    public String getDescription() { return description; }
    public int getDuration() { return duration; }
    public int getWorkloadUnits() { return duration; }
    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public Status getStatus() { return status; }
    public int getGpuId() { return gpuId; }
    public LocalDateTime getStarted() { return started; }
    public LocalDateTime getFinished() { return finished; }
    public int getCharged() { return charged; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
}

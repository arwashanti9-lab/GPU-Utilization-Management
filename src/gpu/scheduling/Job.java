package gpu.scheduling;

import java.io.Serializable;
import java.time.LocalDateTime;

// Immutable submission details with scheduler-owned execution history.
public final class Job implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED
    }

    final String id;
    final String studentId;
    final String title;
    final String application;
    final String description;
    final int duration;
    final LocalDateTime submissionTime;

    Status status = Status.QUEUED;
    Integer gpuId;
    LocalDateTime started;
    LocalDateTime finished;
    LocalDateTime cancelledAt;
    String cancelledBy;
    String cancellationReason;
    String result;
    int charged;

    Job(String id, String studentId, String title, String application,
        String description, int duration, LocalDateTime submissionTime) {
        this.id = id;
        this.studentId = studentId;
        this.title = title;
        this.application = application;
        this.description = description;
        this.duration = duration;
        this.submissionTime = submissionTime;
    }

    Job(Job source) {
        this.id = source.id;
        this.studentId = source.studentId;
        this.title = source.title;
        this.application = source.application;
        this.description = source.description;
        this.duration = source.duration;
        this.submissionTime = source.submissionTime;
        this.status = source.status;
        this.gpuId = source.gpuId;
        this.started = source.started;
        this.finished = source.finished;
        this.cancelledAt = source.cancelledAt;
        this.cancelledBy = source.cancelledBy;
        this.cancellationReason = source.cancellationReason;
        this.result = source.result;
        this.charged = source.charged;
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getTitle() {
        return title;
    }

    public String getApplication() {
        return application;
    }

    public String getDescription() {
        return description;
    }

    public int getDuration() {
        return duration;
    }

    public int getWorkloadUnits() {
        return duration;
    }

    public String getTaskSize() {
        switch (duration) {
            case 5:
                return "Small";
            case 15:
                return "Medium";
            case 30:
                return "Large";
            case 60:
                return "Very large";
            default:
                throw new IllegalStateException("Unsupported job duration.");
        }
    }

    public LocalDateTime getSubmissionTime() {
        return submissionTime;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getGpuId() {
        return gpuId;
    }

    public LocalDateTime getStartTime() {
        return started;
    }

    public LocalDateTime getFinishTime() {
        return finished;
    }

    public LocalDateTime getCancellationTime() {
        return cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getResult() {
        return result;
    }

    public int getChargedMinutes() {
        return charged;
    }
}

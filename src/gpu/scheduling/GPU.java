package gpu.scheduling;

import java.time.LocalDateTime;
//An immutable (unchangable) view of one GPU.
//This is a dashboard view; Scheduler owns the actual assignments.
public record GPU(
        int id,
        String jobId,
        long remainingMinutes,
        LocalDateTime expectedFinish
) {

    public boolean isAvailable() {
        return jobId == null;
    }
}

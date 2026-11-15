package gpu.scheduling;
import java.time.LocalDateTime;
// Stores calculated scheduling information.
// Queue positions start at 1 for waiting jobs.
// Running jobs have position 0 because they have already been dispatched.
// A predicted GPU is only an estimate until the job actually starts.
public record Prediction (
  int quePosition;
  int gpuId;
  LocalDateTime start;
  LocalDateTime finish;
  long remainingWait;
) {

}

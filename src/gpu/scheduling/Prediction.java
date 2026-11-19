package gpu.scheduling;

import java.time.LocalDateTime;

// Stores predicted queue position, GPU and execution times.
public record Prediction(
        int queuePosition,
        int gpuId,
        LocalDateTime start,
        LocalDateTime finish,
        long remainingWait
) {
}

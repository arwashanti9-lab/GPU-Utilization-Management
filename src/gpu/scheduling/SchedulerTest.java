package gpu.scheduling;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDateTime;

/**
 * Dependency-free behavioral checks for Person 3's scheduler.
 *
 * Run this class from your IDE or terminal.
 * A failed check throws AssertionError with an explanation.
 */
public final class SchedulerTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void add(
            Scheduler scheduler,
            String id,
            int duration
    ) {
        scheduler.acceptReservedJob(
                id,
                "student1",
                "Title",
                "Application",
                "Description",
                duration
        );
    }

    private static Job job(Scheduler scheduler, String id) {
        return scheduler.getJobs().stream()
                .filter(record -> record.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void tick(
            Scheduler scheduler,
            int minutes
    ) {
        for (int i = 0; i < minutes; i++) {
            scheduler.advanceMinute();
        }
    }

    public static void main(String[] args) throws Exception {
        LocalDateTime start =
                LocalDateTime.of(2026, 11, 15, 10, 0);

        Scheduler scheduler = new Scheduler(start);

        check(
                scheduler.getUtilization().isEmpty(),
                "Utilization must be N/A at time zero."
        );

        // Occupy all four GPUs.
        for (int i = 1; i <= 4; i++) {
            add(scheduler, "R" + i, 5);
        }

        // Both wait with the same submission time.
        // A must come before Z because job ID breaks timestamp ties.
        add(scheduler, "Z", 15);
        add(scheduler, "A", 5);

        check(
                scheduler.getGpus().stream()
                        .noneMatch(GPU::isAvailable),
                "All four GPUs should be busy."
        );

        check(
                scheduler.predictions().get("A").queuePosition() == 1,
                "FCFS must break equal submission times by job ID."
        );

        check(
                scheduler.predictions().get("A").gpuId() == 1,
                "Prediction must use the lower GPU ID for ties."
        );

        check(
                scheduler.predictions().get("A").remainingWait() == 5,
                "The predicted waiting time should be five minutes."
        );

        check(
                job(scheduler, "A").getGpuId() == null,
                "Prediction must not assign a real GPU to a queued job."
        );

        tick(scheduler, 2);

        check(
                scheduler.getBusyMinutes() == 8,
                "Four busy GPUs for two minutes should total eight."
        );

        scheduler.cancel(
                "R1",
                "student1",
                false,
                "Changed plan"
        );

        check(
                job(scheduler, "R1").getChargedMinutes() == 2,
                "Running cancellation should charge elapsed runtime."
        );

        check(
                job(scheduler, "A").getGpuId() == 1,
                "The next waiting job should start immediately."
        );

        scheduler.cancel(
                "Z",
                "admin",
                true,
                "Not needed"
        );

        check(
                job(scheduler, "Z").getStartTime() == null
                        && job(scheduler, "Z").getChargedMinutes() == 0,
                "Queued cancellation must have no execution or charge."
        );

        // A terminal job cannot be cancelled again.
        try {
            scheduler.cancel(
                    "R1",
                    "admin",
                    true,
                    "Again"
            );

            throw new AssertionError(
                    "Repeated cancellation should be rejected."
            );

        } catch (IllegalStateException expected) {
            // Expected rejection.
        }

        Path snapshot = Files.createTempFile(
                "scheduler-test-",
                ".dat"
        );

        try {
            scheduler.save(snapshot);

            Scheduler restored = Scheduler.load(snapshot);

            check(
                    restored.getNow().equals(scheduler.getNow()),
                    "The simulation clock must pause on restart."
            );

            check(
                    restored.getGpus().equals(scheduler.getGpus()),
                    "Assignments and remaining runtime must be restored."
            );

            tick(scheduler, 5);
            tick(restored, 5);

            check(
                    scheduler.getBusyMinutes()
                            == restored.getBusyMinutes(),
                    "Reloaded execution should produce the same runtime."
            );

            check(
                    job(restored, "A").getStatus()
                            == Job.Status.COMPLETED,
                    "A recovered running job should complete normally."
            );

            check(
                    restored.getBusyMinutes() == 22,
                    "Busy minutes must include cancelled execution."
            );

            double expectedUtilization = 100.0 * 22 / 28;

            check(
                    Math.abs(
                            restored.getUtilization().orElseThrow()
                                    - expectedUtilization
                    ) < 0.00001,
                    "Utilization must use four times elapsed minutes."
            );

            tick(restored, 2);

            check(
                    job(restored, "A").getChargedMinutes() == 5,
                    "Completion charge must stay fixed after more ticks."
            );

        } finally {
            Files.deleteIfExists(snapshot);
        }

        // Check that queued jobs are restored as well as running jobs.
        Scheduler queued = new Scheduler(start);

        for (int i = 1; i <= 4; i++) {
            add(queued, "R" + i, 5);
        }

        add(queued, "Q", 5);
        queued.advanceMinute();

        Path queueFile = Files.createTempFile(
                "queue-test-",
                ".dat"
        );

        try {
            queued.save(queueFile);
            queued = Scheduler.load(queueFile);

            check(
                    queued.predictions().get("Q").remainingWait() == 4,
                    "The restored queue should retain its correct wait."
            );

            tick(queued, 4);

            check(
                    job(queued, "Q").getGpuId() == 1,
                    "The restored queue should dispatch to GPU one."
            );

        } finally {
            Files.deleteIfExists(queueFile);
        }

        // A student cannot cancel another student's job.
        try {
            queued.cancel(
                    "Q",
                    "other",
                    false,
                    "Forbidden"
            );

            throw new AssertionError(
                    "Another student's cancellation should be rejected."
            );

        } catch (SecurityException expected) {
            // Expected rejection.
        }

        // Job IDs must remain unique.
        try {
            add(queued, "Q", 5);

            throw new AssertionError(
                    "Duplicate job IDs should be rejected."
            );

        } catch (IllegalArgumentException expected) {
            // Expected rejection.
        }

        System.out.println(
                "PASS: scheduling, predictions, cancellation, "
                        + "completion, utilization and recovery"
        );
    }
}

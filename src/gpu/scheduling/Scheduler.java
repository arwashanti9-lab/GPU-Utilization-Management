package gpu.scheduling;

import gpu.accounts.JobHistoryProvider;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// Simulates four GPUs using one First Come First Served queue.
// All execution uses the simulation clock. The computer's real clock never advances jobs automatically.
public final class Scheduler
        implements Serializable, JobHistoryProvider {

    private static final long serialVersionUID = 1L;

    public static final int GPU_COUNT = 4;

    private LocalDateTime initialTime;
    private LocalDateTime now;

    // LinkedHashMap retains a predictable order for dashboard/history output.
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    // Several jobs can arrive during one simulated minute. The job ID breaks
    // that tie so saving and reloading cannot change which queued job goes first.
    private static final Comparator<Job> FCFS =
            Comparator.comparing(Job::getSubmissionTime)
                    .thenComparing(Job::getId);

    public Scheduler(LocalDateTime initialTime) {
        initializeClock(initialTime);
    }

    public Scheduler() {
        initialTime = null;
        now = null;
    }

    public synchronized boolean isClockInitialized() {
        return initialTime != null;
    }

    public synchronized void initializeClock(LocalDateTime startingTime) {
        if (isClockInitialized()) {
            throw new IllegalStateException(
                    "The simulation clock has already been initialized."
            );
        }
        initialTime = Objects.requireNonNull(startingTime)
                .truncatedTo(ChronoUnit.MINUTES);
        now = initialTime;
    }

    public synchronized LocalDateTime getNow() {
        return now;
    }

    public synchronized long getElapsedMinutes() {
        if (!isClockInitialized()) {
            return 0;
        }
        return minutes(initialTime, now);
    }

    private void requireClock() {
        if (!isClockInitialized()) {
            throw new IllegalStateException(
                    "The simulation clock has not been initialized."
            );
        }
    }

    private static long minutes(
            LocalDateTime start,
            LocalDateTime end
    ) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "All fields are required."
            );
        }

        return value.trim();
    }

    // Checks submission data without changing the scheduler.
    public synchronized void validateSubmission(
            String id,
            String owner,
            String title,
            String application,
            String description,
            int duration
    ) {
        requireClock();
        id = required(id);
        required(owner);
        required(title);
        required(application);
        required(description);

        if (jobs.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate job ID."
            );
        }

        if (duration != 5
                && duration != 15
                && duration != 30
                && duration != 60) {

            throw new IllegalArgumentException(
                    "Duration must be 5, 15, 30, or 60 minutes."
            );
        }
    }

    /**
     * Adds an already-authorized job to the simulation.
     * Application submissions are coordinated by JobService.
     */
    public synchronized void acceptReservedJob(
            String id,
            String owner,
            String title,
            String application,
            String description,
            int duration
    ) {
        validateSubmission(
                id,
                owner,
                title,
                application,
                description,
                duration
        );

        Job job = new Job(
                id.trim(),
                owner.trim(),
                title.trim(),
                application.trim(),
                description.trim(),
                duration,
                now
        );

        jobs.put(job.id, job);

        // There is no need to wait for Advance Minute if a GPU is already idle.
        // dispatch() still checks the whole queue, so older waiting jobs go first.
        dispatch();
    }

    /**
     * Rebuilds the queue from job records.
     *
     * This also means the queue can be restored after loading a file
     * without maintaining a second, potentially inconsistent queue.
     */
    private List<Job> queue() {
        return jobs.values()
                .stream()
                .filter(job -> job.status == Job.Status.QUEUED)
                .sorted(FCFS)
                .collect(Collectors.toList());
    }

    private Job runningOn(int gpuId) {
        return jobs.values()
                .stream()
                .filter(job ->
                        job.status == Job.Status.RUNNING
                                && job.gpuId == gpuId
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Assigns queued jobs to free GPUs in increasing GPU ID order.
     * Running jobs keep their current GPU and are never preempted.
     */
    private void dispatch() {
        Iterator<Job> waiting = queue().iterator();

        for (int gpuId = 1;
             gpuId <= GPU_COUNT && waiting.hasNext();
             gpuId++) {

            if (runningOn(gpuId) != null) {
                continue;
            }

            Job job = waiting.next();

            job.status = Job.Status.RUNNING;
            job.gpuId = gpuId;
            job.started = now;
        }
    }

    /**
     * Advances exactly one simulated minute.
     *
     * All jobs finishing at this time are completed before waiting
     * jobs are assigned to the newly free GPUs.
     */
    public synchronized void advanceMinute() {
        requireClock();
        // This is the only operation that moves an initialized clock forward.
        // Waiting a minute in real life has no effect on the simulated jobs.
        now = now.plusMinutes(1);

        for (Job job : jobs.values()) {
            if (job.status != Job.Status.RUNNING) {
                continue;
            }

            LocalDateTime expectedFinish =
                    job.started.plusMinutes(job.duration);

            if (!expectedFinish.isAfter(now)) {
                job.finished = now;
                job.charged = job.duration;
                job.result = "Completed successfully (simulated).";
                job.status = Job.Status.COMPLETED;
            }
        }

        dispatch();
    }

    /**
     * Cancels an active job and records who cancelled it and why.
     *
     * The caller must obtain the administrator flag from the
     * authenticated session, never from an untrusted form.
     *
     * Queued cancellation: charge zero and leave execution fields empty.
     * Running cancellation: charge elapsed runtime and retain history.
     */
    public synchronized void cancel(
            String id,
            String actor,
            boolean administrator,
            String reason
    ) {
        Job job = jobs.get(required(id));

        if (job == null) {
            throw new IllegalArgumentException(
                    "Unknown job ID."
            );
        }

        actor = required(actor);
        reason = required(reason);

        if (!administrator && !job.studentId.equals(actor)) {
            throw new SecurityException(
                    "You cannot cancel another student's job."
            );
        }

        if (job.status != Job.Status.QUEUED
                && job.status != Job.Status.RUNNING) {

            throw new IllegalStateException(
                    "This job is already completed or cancelled."
            );
        }

        if (job.status == Job.Status.RUNNING) {
            job.finished = now;

            job.charged = (int) minutes(
                    job.started,
                    now
            );
        }

        job.cancelledAt = now;
        job.cancelledBy = actor;
        job.cancellationReason = reason;
        job.result = "Cancelled: " + reason;
        job.status = Job.Status.CANCELLED;

        // Cancelling a running job may let the next waiting job start now.
        dispatch();
    }

    /**
     * Predicts starts and finishes using COPIED GPU availability times.
     * No real job state or assignment changes during this calculation.
     */
    public synchronized Map<String, Prediction> predictions() {
        Map<String, Prediction> result = new LinkedHashMap<>();
        if (!isClockInitialized()) {
            return Collections.unmodifiableMap(result);
        }

        LocalDateTime[] freeAt = new LocalDateTime[GPU_COUNT];

        // These are forecast times, not the real GPU assignments. Each queued
        // job updates this copy so the next prediction includes the jobs ahead.
        // An idle GPU can take work immediately, so it starts with freeAt = now.
        Arrays.fill(freeAt, now);

        for (Job job : jobs.values()) {
            if (job.status != Job.Status.RUNNING) {
                continue;
            }

            LocalDateTime finish =
                    job.started.plusMinutes(job.duration);

            freeAt[job.gpuId - 1] = finish;

            result.put(
                    job.id,
                    new Prediction(
                            0,
                            job.gpuId,
                            job.started,
                            finish,
                            0
                    )
            );
        }

        int position = 0;

        for (Job job : queue()) {
            int selectedGpu = 0;

            for (int i = 1; i < GPU_COUNT; i++) {
                /*
                 * Only replace the choice for a strictly earlier time.
                 * Equal times therefore keep the lower GPU ID.
                 */
                if (freeAt[i].isBefore(freeAt[selectedGpu])) {
                    selectedGpu = i;
                }
            }

            LocalDateTime predictedStart = freeAt[selectedGpu];

            LocalDateTime predictedFinish =
                    predictedStart.plusMinutes(job.duration);

            // Later queued jobs must account for this predicted assignment.
            freeAt[selectedGpu] = predictedFinish;

            result.put(
                    job.id,
                    new Prediction(
                            ++position,
                            selectedGpu + 1,
                            predictedStart,
                            predictedFinish,
                            minutes(now, predictedStart)
                    )
            );
        }

        return Collections.unmodifiableMap(result);
    }

    public synchronized List<GPU> getGpus() {
        List<GPU> result = new ArrayList<>();

        for (int gpuId = 1; gpuId <= GPU_COUNT; gpuId++) {
            Job job = runningOn(gpuId);

            LocalDateTime finish = job == null
                    ? null
                    : job.started.plusMinutes(job.duration);

            result.add(
                    new GPU(
                            gpuId,
                            job == null ? null : job.id,
                            finish == null ? 0 : minutes(now, finish),
                            finish
                    )
            );
        }

        return List.copyOf(result);
    }

    /**
     * Returns copies, so callers cannot alter the live job records.
     * This full list is intended for administrator/service use.
     */
    public synchronized List<Job> getJobs() {
        return jobs.values()
                .stream()
                .map(Job::new)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Implements the interface already present in gpu.accounts.
     * The account service/UI still controls who can request a student's history.
     */
    @Override
    public synchronized List<String> getJobsForStudent(
            String universityId
    ) {
        return jobs.values()
                .stream()
                .filter(job ->
                        job.studentId.equals(universityId)
                )
                .map(job ->
                        job.id
                                + " | " + job.title
                                + " | " + job.status
                                + " | GPU " + job.gpuId
                )
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Includes all actual execution:
     * completed jobs, running jobs, and jobs cancelled while running.
     *
     * Waiting time is excluded because those jobs were not using a GPU.
     */
    public synchronized long getBusyMinutes() {
        long busy = 0;

        for (Job job : jobs.values()) {
            if (job.started == null) {
                continue;
            }

            LocalDateTime end = job.finished == null
                    ? now
                    : job.finished;

            busy += minutes(job.started, end);
        }

        return busy;
    }

    public synchronized OptionalDouble getUtilization() {
        long elapsed = getElapsedMinutes();

        // The formula would divide by zero before the first minute.
        if (elapsed == 0) {
            return OptionalDouble.empty();
        }

        // Four GPUs running for ten minutes could provide forty GPU minutes.
        // Divide actual execution by that capacity; queueing uses no GPU time.
        double utilization =
                100.0 * getBusyMinutes()
                        / (GPU_COUNT * (double) elapsed);

        return OptionalDouble.of(utilization);
    }

    /**
     * Saves the clock and all execution records together.
     *
     * Writing a temporary file first avoids overwriting a good snapshot
     * with a partially written file.
     */
    public synchronized void save(Path file) throws IOException {
        Path target = file.toAbsolutePath();

        Files.createDirectories(target.getParent());

        Path temporary = Files.createTempFile(
                target.getParent(),
                "simulation-",
                ".tmp"
        );

        try {
            try (ObjectOutputStream output = new ObjectOutputStream(
                    Files.newOutputStream(temporary)
            )) {
                output.writeObject(this);
            }

            // Replace the old snapshot only after the new one is fully written.
            // If the disk write fails earlier, the previous snapshot is kept.
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Loads the exact saved simulated time.
     *
     * Running assignments and start times are retained. The queue is
     * reconstructed from saved jobs when needed.
     *
     * Only load snapshot files created by this application that you trust.
     */
    public static Scheduler load(Path file) throws IOException {
        try (ObjectInputStream input = new ObjectInputStream(
                Files.newInputStream(file)
        )) {
            Object value = input.readObject();

            if (!(value instanceof Scheduler)) {
                throw new IOException(
                        "This file is not a simulation snapshot."
                );
            }

            return (Scheduler) value;

        } catch (ClassNotFoundException | RuntimeException exception) {
            throw new IOException(
                    "Cannot load simulation: " + exception.getMessage(),
                    exception
            );
        }
    }
}

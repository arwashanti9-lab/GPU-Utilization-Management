package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.Role;
import gpu.accounts.User;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

// Coordinates authenticated job actions, allowances and scheduler persistence.
public final class JobService {
    private final AccountService accounts;
    private final Scheduler scheduler;
    private final Path snapshot;

    public JobService(
            AccountService accounts,
            Scheduler scheduler,
            Path snapshot
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.snapshot = Objects.requireNonNull(snapshot).toAbsolutePath();
    }

    public synchronized String submitJob(
            String title,
            String application,
            String description,
            int duration
    ) throws IOException {
        User user = currentStudent();
        String studentId = user.getUsername();
        String jobId = nextJobId();

        scheduler.validateSubmission(
                jobId, studentId, title, application, description, duration
        );
        accounts.reserveForJob(studentId, jobId, duration);
        scheduler.acceptReservedJob(
                jobId, studentId, title, application, description, duration
        );
        scheduler.save(snapshot);
        return jobId;
    }

    public synchronized void cancelJob(
            String jobId,
            String reason
    ) throws IOException {
        User user = currentUser();
        boolean admin = user.getRole() == Role.ADMINISTRATOR;

        scheduler.cancel(jobId, user.getUsername(), admin, reason);
        Job job = findJob(jobId);
        accounts.settleForJob(job.getId(), job.getChargedMinutes());
        scheduler.save(snapshot);
    }

    public synchronized void advanceMinute() throws IOException {
        requireAdmin();

        List<Job> before = scheduler.getJobs();
        scheduler.advanceMinute();
        Map<String, Job> after = indexJobs(scheduler.getJobs());

        for (Job previous : before) {
            if (previous.getStatus() != Job.Status.RUNNING) {
                continue;
            }

            Job current = after.get(previous.getId());
            if (current != null
                    && current.getStatus() == Job.Status.COMPLETED) {
                accounts.settleForJob(
                        current.getId(),
                        current.getChargedMinutes()
                );
            }
        }

        scheduler.save(snapshot);
    }

    public synchronized void save() throws IOException {
        scheduler.save(snapshot);
    }

    public List<Job> getJobs() {
        User user = currentUser();
        List<Job> result = scheduler.getJobs();
        if (user.getRole() == Role.ADMINISTRATOR) {
            return result;
        }
        return result.stream()
                .filter(job -> job.getStudentId().equals(user.getUsername()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    public List<GPU> getGpus() {
        requireAdmin();
        return scheduler.getGpus();
    }

    public Map<String, Prediction> getPredictions() {
        User user = currentUser();
        Map<String, Prediction> result = scheduler.predictions();
        if (user.getRole() == Role.ADMINISTRATOR) {
            return result;
        }

        java.util.Set<String> visibleIds = new java.util.HashSet<>();
        for (Job job : getJobs()) {
            visibleIds.add(job.getId());
        }
        java.util.Map<String, Prediction> visible = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Prediction> entry : result.entrySet()) {
            if (visibleIds.contains(entry.getKey())) {
                visible.put(entry.getKey(), entry.getValue());
            }
        }
        return java.util.Collections.unmodifiableMap(visible);
    }

    public LocalDateTime getNow() {
        return scheduler.getNow();
    }

    public long getElapsedMinutes() {
        requireAdmin();
        return scheduler.getElapsedMinutes();
    }

    public long getBusyMinutes() {
        requireAdmin();
        return scheduler.getBusyMinutes();
    }

    public OptionalDouble getUtilization() {
        requireAdmin();
        return scheduler.getUtilization();
    }

    private User currentStudent() {
        User user = currentUser();
        if (user.getRole() != Role.STUDENT) {
            throw new SecurityException(
                    "A student must be logged in to submit a job."
            );
        }
        return user;
    }

    private User currentUser() {
        User user = accounts.getCurrentUser();
        if (user == null) {
            throw new SecurityException("Login is required.");
        }
        return user;
    }

    private void requireAdmin() {
        if (currentUser().getRole() != Role.ADMINISTRATOR) {
            throw new SecurityException(
                    "Administrator login is required to advance time."
            );
        }
    }

    private Job findJob(String id) {
        String key = id == null ? "" : id.trim();
        for (Job job : scheduler.getJobs()) {
            if (job.getId().equals(key)) {
                return job;
            }
        }
        throw new IllegalArgumentException("Job not found.");
    }

    private static Map<String, Job> indexJobs(List<Job> jobs) {
        return jobs.stream().collect(
                java.util.stream.Collectors.toMap(
                        Job::getId,
                        job -> job
                )
        );
    }

    private String nextJobId() {
        int highest = 0;
        for (Job job : scheduler.getJobs()) {
            String id = job.getId();
            if (id.matches("JOB-[0-9]{6}")) {
                highest = Math.max(
                        highest,
                        Integer.parseInt(id.substring(4))
                );
            }
        }

        if (highest >= 999999) {
            throw new IllegalStateException("No more job IDs available.");
        }
        return String.format("JOB-%06d", highest + 1);
    }
}

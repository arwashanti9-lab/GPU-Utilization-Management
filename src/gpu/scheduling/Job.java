package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.Role;
import gpu.accounts.User;

import java.io.IOException;
import java.util.List;

// Connects job actions with student allowances and the scheduler.
public class JobService {
    private final AccountService accounts;
    private final Scheduler scheduler;

    public JobService(
            AccountService accounts,
            Scheduler scheduler
    ) {
        if (accounts == null || scheduler == null) {
            throw new IllegalArgumentException(
                    "Accounts and scheduler are required."
            );
        }

        this.accounts = accounts;
        this.scheduler = scheduler;
    }

    // Checks a student's job, reserves its minutes and submits it.
    // The calling code must save the scheduler after submission.
    public String submitJob(
            String title,
            String application,
            String description,
            int duration
    ) throws IOException {

        User user = accounts.getCurrentUser();

        // Only a signed-in student can submit jobs.
        if (user == null || user.getRole() != Role.STUDENT) {
            throw new SecurityException(
                    "A student must be logged in to submit a job."
            );
        }

        String studentId = user.getUsername();
        String jobId = nextJobId();

        // Check the job details before changing the balance.
        scheduler.validateSubmission(
                jobId,
                studentId,
                title,
                application,
                description,
                duration
        );

        // Reserve the job's full duration.
        accounts.reserveForJob(
                studentId,
                jobId,
                duration
        );

        // Add the accepted job to the scheduler.
        scheduler.acceptReservedJob(
                jobId,
                studentId,
                title,
                application,
                description,
                duration
        );

        return jobId;
    }

    // Cancels a job and updates its owner's allowance.
    public void cancelJob(
            String jobId,
            String reason
    ) throws IOException {

        User user = accounts.getCurrentUser();

        if (user == null) {
            throw new SecurityException("Login is required.");
        }

        // The scheduler uses this role to check cancellation permission.
        boolean admin = user.getRole() == Role.ADMINISTRATOR;

        scheduler.cancel(
                jobId,
                user.getUsername(),
                admin,
                reason
        );

        Job job = findJob(jobId);

        // Release the reservation and charge only the runtime used.
        accounts.settleForJob(
                job.getId(),
                job.getChargedMinutes()
        );
    }

    // Advances the clock and charges jobs completed during that minute.
    public void advanceMinute() throws IOException {
        User user = accounts.getCurrentUser();

        // Only an administrator can advance the shared clock.
        if (user == null
                || user.getRole() != Role.ADMINISTRATOR) {

            throw new SecurityException(
                    "Administrator login is required to advance time."
            );
        }

        // getJobs() returns copies, preserving the previous job states.
        List<Job> before = scheduler.getJobs();

        scheduler.advanceMinute();

        List<Job> after = scheduler.getJobs();

        // Find jobs that changed from running to completed.
        for (Job previous : before) {
            if (previous.getStatus() != Job.Status.RUNNING) {
                continue;
            }

            for (Job current : after) {
                if (current.getId().equals(previous.getId())
                        && current.getStatus() == Job.Status.COMPLETED) {

                    accounts.settleForJob(
                            current.getId(),
                            current.getChargedMinutes()
                    );
                }
            }
        }
    }

    // Finds the job with the given ID.
    private Job findJob(String id) {
        for (Job job : scheduler.getJobs()) {
            if (job.getId().equals(id.trim())) {
                return job;
            }
        }

        throw new IllegalArgumentException("Job not found.");
    }

    // Generates the next ID in the format JOB-000001.
    private String nextJobId() {
        int highest = 0;

        for (Job job : scheduler.getJobs()) {
            String id = job.getId();

            if (id.matches("JOB-[0-9]{6}")) {
                int number = Integer.parseInt(id.substring(4));

                if (number > highest) {
                    highest = number;
                }
            }
        }

        if (highest >= 999999) {
            throw new IllegalStateException(
                    "No more job IDs available."
            );
        }

        return String.format("JOB-%06d", highest + 1);
    }
}

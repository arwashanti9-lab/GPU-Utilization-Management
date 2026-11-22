package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.FileAccountRepository;
import gpu.accounts.TimeAllowance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

// Dependency-free integration checks for account allowances and job actions.
public final class JobServiceTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("gpu-job-service-test-");
        Path accountFile = directory.resolve("accounts.dat");
        Path schedulerFile = directory.resolve("simulation.dat");

        try {
            AccountService accounts = new AccountService(
                    new FileAccountRepository(accountFile)
            );
            accounts.register(
                    "S-1", "Student One", "one@example.edu",
                    "+971500000001", "student-pass"
            );
            accounts.register(
                    "S-2", "Student Two", "two@example.edu",
                    "+971500000002", "student-pass"
            );

            accounts.login("admin", "gpu2026");
            accounts.approve("S-1");
            accounts.approve("S-2");
            accounts.setAllocation("S-1", 15, "Exact-match integration test");
            accounts.logout();

            Scheduler scheduler = new Scheduler(
                    LocalDateTime.of(2026, 11, 20, 9, 0)
            );
            JobService jobs = new JobService(
                    accounts, scheduler, schedulerFile
            );

            accounts.login("S-1", "student-pass");
            String runningJob = jobs.submitJob(
                    "Long job", "Training", "Running cancellation test", 15
            );
            check(accounts.getAllowance("S-1").getAvailable() == 0,
                    "A job matching the exact balance must be accepted.");
            try {
                jobs.submitJob(
                        "Over allowance", "Training",
                        "Must be rejected", 5
                );
                throw new AssertionError(
                        "A job exceeding the available balance must be rejected."
                );
            } catch (IllegalArgumentException expected) {
                check(accounts.getAllowance("S-1").getReserved() == 15,
                        "A rejected job must not reserve minutes.");
            }
            check(jobs.getJobs().size() == 1,
                    "Students must see only their own jobs.");
            try {
                jobs.advanceMinute();
                throw new AssertionError(
                        "Students must not advance the shared simulation clock."
                );
            } catch (SecurityException expected) {
                // Expected permission check.
            }
            accounts.logout();

            accounts.login("S-2", "student-pass");
            String completedJob = jobs.submitJob(
                    "Short job", "Training", "Completion test", 5
            );
            String adminCancelledJob = jobs.submitJob(
                    "Medium job 1", "Training", "Fill another GPU", 15
            );
            jobs.submitJob(
                    "Medium job 2", "Training", "Fill another GPU", 15
            );
            String queuedJob = jobs.submitJob(
                    "Queued job", "Training", "Queued cancellation test", 15
            );
            check(jobs.getJobs().size() == 4,
                    "Students must see all of their own jobs.");
            check(jobs.getPredictions().get(queuedJob).remainingWait() == 5,
                    "Queued wait must include the earliest GPU availability.");
            try {
                jobs.cancelJob(runningJob, "Attempt to cancel another student");
                throw new AssertionError(
                        "Students must not cancel another student's job."
                );
            } catch (SecurityException expected) {
                // Expected permission check.
            }

            jobs.cancelJob(queuedJob, "Queued cancellation test");
            TimeAllowance afterQueuedCancel = accounts.getAllowance("S-2");
            check(afterQueuedCancel.getUsed() == 0,
                    "Queued cancellation must not charge runtime.");
            check(afterQueuedCancel.getReserved() == 35,
                    "Queued cancellation must release its full reservation.");

            check(jobs.getJobs().size() == 4,
                    "Cancelled jobs must remain visible in the student's history.");
            accounts.logout();

            accounts.login("admin", "gpu2026");
            check(jobs.getGpus().stream().noneMatch(GPU::isAvailable),
                    "All four GPUs must be occupied before advancing.");
            check(jobs.getJobs().size() == 5,
                    "Administrators must see all students' jobs.");
            for (int i = 0; i < 2; i++) {
                jobs.advanceMinute();
            }
            jobs.cancelJob(
                    adminCancelledJob,
                    "Administrator running cancellation test"
            );
            accounts.logout();

            accounts.login("S-1", "student-pass");
            jobs.cancelJob(runningJob, "Student changed plans");
            TimeAllowance firstAllowance = accounts.getAllowance("S-1");
            check(firstAllowance.getUsed() == 2,
                    "Running cancellation must charge elapsed runtime.");
            check(firstAllowance.getReserved() == 0,
                    "Cancellation must release the complete reservation.");
            check(firstAllowance.getAvailable() == 13,
                    "Running cancellation returns unspent reserved minutes.");
            // Try to settle the cancelled job a second time.
                    try {accounts.settleForJob(runningJob, 2);
                        throw new AssertionError("A job must not be settled twice.");
                } catch (IllegalArgumentException expected) {
    // The account service should reject the second settlement.
                            }

        // Check that rejecting the second settlement did not change the balance.
                TimeAllowance afterSecondAttempt = accounts.getAllowance("S-1");

                check(afterSecondAttempt.getUsed() == 2,
                        "A second settlement must not charge the student again.");

                    check(afterSecondAttempt.getReserved() == 0,
                        "A second settlement must not change the reservation.");

                    check(afterSecondAttempt.getAvailable() == 13,
                        "A second settlement must not change the available balance.");
     accounts.logout();

            accounts.login("admin", "gpu2026");
            for (int i = 0; i < 3; i++) {
                jobs.advanceMinute();
            }
            accounts.logout();

            accounts.login("S-2", "student-pass");
            TimeAllowance secondAllowance = accounts.getAllowance("S-2");
            check(secondAllowance.getUsed() == 7,
                    "Completion and administrator cancellation must both be charged.");
            check(secondAllowance.getReserved() == 15,
                    "Settled jobs release reservations while other jobs remain reserved.");
            check(jobs.getJobs().stream()
                            .anyMatch(job -> job.getId().equals(completedJob)
                                    && job.getStatus() == Job.Status.COMPLETED),
                    "The completed job must remain visible in history.");
            check(jobs.getJobs().stream()
                            .anyMatch(job -> job.getId().equals(adminCancelledJob)
                                    && job.getStatus() == Job.Status.CANCELLED
                                    && job.getChargedMinutes() == 2
                                    && "admin".equals(job.getCancelledBy())),
                    "Administrator running cancellation must record its actor and charge.");
            check(jobs.getJobs().stream()
                            .anyMatch(job -> job.getId().equals(queuedJob)
                                    && job.getStatus() == Job.Status.CANCELLED),
                    "Queued cancellations must remain in job history.");
            accounts.logout();

            AccountService restoredAccounts = new AccountService(
                    new FileAccountRepository(accountFile)
            );
            Scheduler restoredScheduler = Scheduler.load(schedulerFile);
            restoredAccounts.login("admin", "gpu2026");
            JobService restoredJobs = new JobService(
                    restoredAccounts, restoredScheduler, schedulerFile
            );
            check(restoredAccounts.getAllowance("S-1").getUsed() == 2,
                    "Allowance charges must survive application restart.");
            check(restoredAccounts.getAllowance("S-2").getUsed() == 7,
                    "Administrator cancellation charges must survive restart.");
            check(restoredJobs.getJobs().size() == 5,
                    "Job history and cancellation records must survive restart.");
            check(restoredScheduler.getNow().equals(
                            LocalDateTime.of(2026, 11, 20, 9, 5)),
                    "The persisted simulation clock must be restored.");
            check(restoredScheduler.getGpus().equals(scheduler.getGpus()),
                    "Running GPU assignments and remaining times must be restored.");

            System.out.println(
                    "PASS: allowance reservation, cancellation, completion, "
                            + "permissions and persistence"
            );
        } finally {
            Files.deleteIfExists(schedulerFile);
            Files.deleteIfExists(accountFile);
            Files.deleteIfExists(directory);
        }
    }
}

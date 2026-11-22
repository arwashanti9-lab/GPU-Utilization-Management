
package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.FileAccountRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

// This class creates sample students and GPU jobs so we can demonstrate
// how the system works without having to enter everything manually.
public final class CreateDemoData {

    public static void main(String[] args) throws Exception {

        // These are the files where we save our sample accounts and jobs.
        // We can also provide different file names when running the program.
        Path accountFile = Paths.get(
                args.length == 0 ? "data/demo-accounts.dat" : args[0]
        );

        Path schedulerFile = Paths.get(
                args.length < 2 ? "data/demo-simulation.dat" : args[1]
        );

        // We don't want to accidentally overwrite existing student
        // information or simulation data, so we check the files first.
        // If either file already exists, the program stops.
        if (Files.exists(accountFile) || Files.exists(schedulerFile)) {
            throw new IllegalArgumentException(
                    "Choose new output paths; sample files already exist."
            );
        }

        // Set up the account service and tell it where to save
        // the student information that we are about to create.
        AccountService accounts = new AccountService(
                new FileAccountRepository(accountFile)
        );

        // Create five sample students, S1 to S5.
        // Each student gets a unique ID, email address and phone number.
        // We use the same password to make testing the accounts easier.
        for (int i = 1; i <= 5; i++) {
            String id = "S" + i;

            accounts.register(
                    id,
                    "Sample Student " + i,
                    "student" + i + "@example.edu",
                    "+97150000000" + i,
                    "student123"
            );
        }

        // Create two more students to test the registration process.
        // One will stay pending, while the other will be rejected.
        // This helps us show how the admin handles different requests.
        accounts.register(
                "S-PENDING", "Pending Student", "pending@example.edu",
                "+971500000006", "student123"
        );

        accounts.register(
                "S-REJECTED", "Rejected Student", "rejected@example.edu",
                "+971500000007", "student123"
        );

        // Log in as the administrator to review the registrations.
        // We approve the first five students so they can submit GPU jobs.
        accounts.login("admin", "gpu2026");

        for (int i = 1; i <= 5; i++) {
            accounts.approve("S" + i);
        }

        // Reject one request and leave the pending student untouched.
        // This gives us examples of all three registration statuses.
        accounts.reject("S-REJECTED");

        // Log out because the admin has finished reviewing accounts.
        accounts.logout();

        // Create the GPU scheduler and set a starting date and time.
        // We use a fixed time so the simulation is easy to repeat
        // and we can check that the results are consistent.
        Scheduler scheduler = new Scheduler(
                LocalDateTime.of(2026, 11, 20, 9, 0)
        );

        // JobService connects the accounts to the scheduler.
        // This makes sure that GPU jobs and student allowances
        // are handled together instead of separately.
        JobService jobs = new JobService(
                accounts, scheduler, schedulerFile
        );

        // Save the initial scheduler state before adding any jobs.
        jobs.save();

        // Create 20 sample GPU jobs across the five approved students.
        // Each student submits four jobs.
        // The first four jobs take 5 minutes each, and the rest take 15.
        for (int i = 1; i <= 20; i++) {

            // Cycle through S1 to S5 so the jobs are shared
            // between students instead of belonging to just one.
            String studentId = "S" + ((i - 1) % 5 + 1);

            // Log in as the student before submitting their job.
            // This also lets the system check their permissions
            // and whether they have enough GPU minutes available.
            accounts.login(studentId, "student123");

            jobs.submitJob(
                    "Sample task " + i,
                    "Training",
                    "Demonstration job " + i,
                    i <= 4 ? 5 : 15
            );

            // Log out before moving on to the next student's job.
            accounts.logout();
        }

        // Log in as admin and move the simulation forward by 6 minutes.
        // The scheduler uses this time to update running jobs,
        // complete jobs that have finished and assign waiting jobs.
        accounts.login("admin", "gpu2026");

        for (int i = 0; i < 6; i++) {
            jobs.advanceMinute();
        }

        accounts.logout();

        // Test a student cancelling one of their own jobs.
        // This lets us check that cancellation updates the job
        // and handles any reserved GPU minutes correctly.
        accounts.login("S5", "student123");

        jobs.cancelJob(
                "JOB-000005",
                "Student cancellation example"
        );

        accounts.logout();

        // Test an administrator cancelling a job.
        // Unlike students, the administrator is allowed to
        // manage jobs belonging to other users.
        accounts.login("admin", "gpu2026");

        jobs.cancelJob(
                "JOB-000020",
                "Administrator cancellation example"
        );

        accounts.logout();

        // Print the saved file locations in the Eclipse Console.
        // This helps us confirm where the demonstration data went.
        System.out.println("Saved sample accounts to "
                + accountFile.toAbsolutePath());

        System.out.println("Saved sample jobs to "
                + schedulerFile.toAbsolutePath());

        // Display the sample login details so we can easily
        // access the demonstration accounts when testing.
        System.out.println(
                "Sample logins: admin / gpu2026; S1-S5 / student123"
        );
    }
}

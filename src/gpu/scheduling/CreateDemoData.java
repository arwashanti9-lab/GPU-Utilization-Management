package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.FileAccountRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

// Creates a complete, allowance-consistent sample account and job dataset.
public final class CreateDemoData {
    public static void main(String[] args) throws Exception {
        Path accountFile = Paths.get(
                args.length == 0 ? "data/demo-accounts.dat" : args[0]
        );
        Path schedulerFile = Paths.get(
                args.length < 2 ? "data/demo-simulation.dat" : args[1]
        );

        if (Files.exists(accountFile) || Files.exists(schedulerFile)) {
            throw new IllegalArgumentException(
                    "Choose new output paths; sample files already exist."
            );
        }

        AccountService accounts = new AccountService(
                new FileAccountRepository(accountFile)
        );

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
        accounts.register(
                "S-PENDING", "Pending Student", "pending@example.edu",
                "+971500000006", "student123"
        );
        accounts.register(
                "S-REJECTED", "Rejected Student", "rejected@example.edu",
                "+971500000007", "student123"
        );

        accounts.login("admin", "gpu2026");
        for (int i = 1; i <= 5; i++) {
            accounts.approve("S" + i);
        }
        accounts.reject("S-REJECTED");
        accounts.logout();

        Scheduler scheduler = new Scheduler(
                LocalDateTime.of(2026, 11, 20, 9, 0)
        );
        JobService jobs = new JobService(accounts, scheduler, schedulerFile);
        jobs.save();

        for (int i = 1; i <= 20; i++) {
            String studentId = "S" + ((i - 1) % 5 + 1);
            accounts.login(studentId, "student123");
            jobs.submitJob(
                    "Sample task " + i,
                    "Training",
                    "Demonstration job " + i,
                    i <= 4 ? 5 : 15
            );
            accounts.logout();
        }

        accounts.login("admin", "gpu2026");
        for (int i = 0; i < 6; i++) {
            jobs.advanceMinute();
        }
        accounts.logout();

        accounts.login("S5", "student123");
        jobs.cancelJob("JOB-000005", "Student cancellation example");
        accounts.logout();

        accounts.login("admin", "gpu2026");
        jobs.cancelJob("JOB-000020", "Administrator cancellation example");
        accounts.logout();

        System.out.println("Saved sample accounts to "
                + accountFile.toAbsolutePath());
        System.out.println("Saved sample jobs to "
                + schedulerFile.toAbsolutePath());
        System.out.println(
                "Sample logins: admin / gpu2026; S1-S5 / student123"
        );
    }
}

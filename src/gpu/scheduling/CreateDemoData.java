package gpu.scheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;

/**
 * Generates sample scheduling data for Person 3's demonstration.
 *
 * Final snapshot:
 * 4 completed jobs
 * 4 running jobs
 * 10 queued jobs
 * 2 cancelled jobs
 */
public final class CreateDemoData {

    public static void main(String[] args) throws Exception {
        Path file = Paths.get(
                args.length == 0
                        ? "data/person3-demo.dat"
                        : args[0]
        );

        // Avoid accidentally replacing an existing demonstration snapshot.
        if (Files.exists(file)) {
            throw new IllegalArgumentException(
                    "Choose a new path; demo file already exists."
            );
        }

        Scheduler scheduler = new Scheduler(
                LocalDateTime.of(2026, 11, 15, 9, 0)
        );

        for (int i = 1; i <= 20; i++) {
            // Padded IDs make lexicographic ordering easy to understand.
            String id = String.format("J%03d", i);

            // Distribute jobs across S1, S2, S3, S4, and S5.
            String owner = "S" + ((i - 1) % 5 + 1);

            // The first four jobs finish after five minutes.
            int duration = i <= 4 ? 5 : 15;

            scheduler.acceptReservedJob(
                    id,
                    owner,
                    "Demo task " + i,
                    "Training",
                    "Scheduling demonstration",
                    duration
            );
        }

        // Cancel a waiting job: zero charge and no execution timestamps.
        scheduler.cancel(
                "J020",
                "admin",
                true,
                "Queued cancellation example"
        );

        for (int minute = 0; minute < 6; minute++) {
            scheduler.advanceMinute();
        }

        // J005 started at minute five, so this cancellation charges one minute.
        scheduler.cancel(
                "J005",
                "admin",
                true,
                "Running cancellation example"
        );

        scheduler.save(file);

        System.out.println(
                "Saved demo snapshot to " + file.toAbsolutePath()
        );
    }
}

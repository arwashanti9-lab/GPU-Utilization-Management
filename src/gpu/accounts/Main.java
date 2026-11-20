package gpu.accounts;

import gpu.scheduling.JobService;
import gpu.scheduling.Scheduler;

import java.nio.file.*;
import javax.swing.*;

//Starts the application and opens the login window.
public final class Main {
    public static void main(String[] args) {

        // Creates Swing components on Swing's event-handling thread.
        SwingUtilities.invokeLater(() -> {
            try {
                // Uses the computer's standard appearance for the interface.
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );

                Path accountFile = Paths.get(
                        args.length == 0
                                ? "data/accounts.dat"
                                : args[0]
                );

                AccountService service = new AccountService(
                        new FileAccountRepository(accountFile)
                );

                Path schedulerFile = Paths.get(
                        args.length < 2
                                ? "data/simulation.dat"
                                : args[1]
                );

                boolean newSimulation = !Files.exists(schedulerFile);
                Scheduler scheduler = newSimulation
                        ? new Scheduler()
                        : Scheduler.load(schedulerFile);

                JobService jobs = new JobService(
                        service, scheduler, schedulerFile
                );

                if (newSimulation) {
                    jobs.save();
                }

                new AccountFrame(service, jobs).setVisible(true);

            } catch (Exception exception) {
                // Reports startup errors without deleting the saved data.
                JOptionPane.showMessageDialog(
                        null,
                        "Cannot start the application: "
                                + exception.getMessage()
                                + "\nCheck the data-file path and permissions."
                                + "\nExisting data was not reset.",
                        "Startup error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

}

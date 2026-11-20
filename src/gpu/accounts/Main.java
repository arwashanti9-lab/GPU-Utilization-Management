package gpu.accounts;

import java.nio.file.*;
import java.util.*;
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

                // Uses the default account file unless another path is supplied.
                Path file = Paths.get(
                        args.length == 0
                                ? "data/accounts.dat"
                                : args[0]
                );
                
                // Creates an AccountService that reads and saves accounts using this file.
                AccountService service = new AccountService(
                        new FileAccountRepository(file)
                );

                // TEMPORARY CODE.
                JobHistoryProvider jobs = id -> List.of(
                        "Job module has not been connected yet."
                );

                // Creates and displays the application window.
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

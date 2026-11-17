package gpu.scheduling;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;

import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Date;
import java.util.Map;

/**
 * Administrator demonstration dashboard for Person 3.
 *
 * Keep scheduling logic in Scheduler, not in Swing event handlers.
 * Real submissions and student access must be connected by the team.
 */
public final class SchedulerFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private Scheduler scheduler;
    private final Path file;

    private final JLabel summary = new JLabel();

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DefaultTableModel gpuModel = model(
            "GPU",
            "Availability",
            "Job",
            "Remaining",
            "Expected finish"
    );

    private final DefaultTableModel jobModel = model(
            "ID",
            "Student",
            "Title",
            "Application",
            "Status",
            "GPU",
            "Queue",
            "Predicted start",
            "Predicted finish",
            "Wait left",
            "Submitted",
            "Started",
            "Finished",
            "Runtime",
            "Queue wait",
            "Charge",
            "Cancelled at",
            "Cancelled by",
            "Reason"
    );

    /**
     * Makes table cells read-only.
     * Changing table text must never modify a real job.
     */
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private SchedulerFrame(Scheduler scheduler, Path file) {
        super("Person 3 - GPU scheduling administrator demo");

        this.scheduler = scheduler;
        this.file = file;

        setSize(1400, 720);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel controls = new JPanel();

        addButton(
                controls,
                "Advance Minute",
                () -> this.scheduler.advanceMinute()
        );

        addButton(controls, "Add demo job", this::submit);
        addButton(controls, "Cancel job", this::cancel);

        addButton(
                controls,
                "Save",
                () -> this.scheduler.save(this.file)
        );

        addButton(
                controls,
                "Reload",
                () -> this.scheduler = Scheduler.load(this.file)
        );

        controls.add(
                new JLabel(
                        "Demo submissions assume allowance is reserved."
                )
        );

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(summary);
        top.add(controls);

        add(top, BorderLayout.NORTH);

        JTable jobTable = new JTable(jobModel);

        // Horizontal scrolling keeps all detailed history columns accessible.
        jobTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(new JTable(gpuModel)),
                new JScrollPane(jobTable)
        );

        split.setDividerLocation(150);

        add(split, BorderLayout.CENTER);

        // Ask about saving so closing does not silently discard changes.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }
        });

        refresh();
        setLocationRelativeTo(null);
    }

    /**
     * Allows button handlers to report checked exceptions, such as file errors.
     */
    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private void addButton(
            JPanel panel,
            String label,
            Action action
    ) {
        JButton button = new JButton(label);

        button.addActionListener(event -> {
            try {
                action.run();
                refresh();
            } catch (Exception exception) {
                showError(exception);
            }
        });

        panel.add(button);
    }

    private void showError(Exception exception) {
        JOptionPane.showMessageDialog(
                this,
                exception.getMessage(),
                "Operation failed",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void closeWindow() {
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Save simulation before closing?",
                "Save",
                JOptionPane.YES_NO_CANCEL_OPTION
        );

        if (answer == JOptionPane.CANCEL_OPTION
                || answer == JOptionPane.CLOSED_OPTION) {
            return;
        }

        try {
            if (answer == JOptionPane.YES_OPTION) {
                scheduler.save(file);
            }

            dispose();

        } catch (Exception exception) {
            // Keep the window open if saving fails.
            showError(exception);
        }
    }

    /**
     * Demonstration input only.
     *
     * In the final application, Person 2 must generate the ID, authenticate
     * the student, check allowance, and reserve time before acceptance.
     */
    private void submit() {
        JTextField id = new JTextField();
        JTextField owner = new JTextField();
        JTextField title = new JTextField();
        JTextField application = new JTextField();
        JTextField description = new JTextField();

        JComboBox<String> size = new JComboBox<>(
                new String[]{
                        "Small - 5 minutes",
                        "Medium - 15 minutes",
                        "Large - 30 minutes",
                        "Very large - 60 minutes"
                }
        );

        Object[] fields = {
                "Unique job ID", id,
                "Student ID", owner,
                "Title", title,
                "Application", application,
                "Description", description,
                "Task size / confirmed duration", size
        };

        int answer = JOptionPane.showConfirmDialog(
                this,
                fields,
                "Add already-reserved demo job",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        int[] durations = {5, 15, 30, 60};

        scheduler.acceptReservedJob(
                id.getText(),
                owner.getText(),
                title.getText(),
                application.getText(),
                description.getText(),
                durations[size.getSelectedIndex()]
        );
    }

    private void cancel() {
        JTextField id = new JTextField();
        JTextField reason = new JTextField();

        int answer = JOptionPane.showConfirmDialog(
                this,
                new Object[]{
                        "Job ID", id,
                        "Reason", reason
                },
                "Administrator cancellation",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (answer == JOptionPane.OK_OPTION) {
            // This entire window is an administrator demo.
            scheduler.cancel(
                    id.getText(),
                    "admin",
                    true,
                    reason.getText()
            );
        }
    }

    private static String time(LocalDateTime value) {
        return value == null ? "-" : FORMAT.format(value);
    }

    /**
     * Rebuilds the displayed data after every successful operation.
     * Predictions are recalculated from the current clock and records.
     */
    private void refresh() {
        Map<String, Prediction> predictions =
                scheduler.predictions();

        long[] counts = new long[Job.Status.values().length];

        gpuModel.setRowCount(0);
        jobModel.setRowCount(0);

        for (GPU gpu : scheduler.getGpus()) {
            gpuModel.addRow(
                    new Object[]{
                            gpu.id(),
                            gpu.isAvailable() ? "Free" : "Busy",
                            gpu.jobId(),
                            gpu.remainingMinutes(),
                            time(gpu.expectedFinish())
                    }
            );
        }

        for (Job job : scheduler.getJobs()) {
            counts[job.getStatus().ordinal()]++;

            Prediction prediction = predictions.get(job.getId());

            LocalDateTime start = job.getStartTime();
            LocalDateTime finish = job.getFinishTime();

            long runtime = start == null
                    ? 0
                    : ChronoUnit.MINUTES.between(
                            start,
                            finish == null
                                    ? scheduler.getNow()
                                    : finish
                    );

            Long waited = start == null
                    ? null
                    : ChronoUnit.MINUTES.between(
                            job.getSubmissionTime(),
                            start
                    );

            jobModel.addRow(
                    new Object[]{
                            job.getId(),
                            job.getStudentId(),
                            job.getTitle(),
                            job.getApplication(),
                            job.getStatus(),
                            job.getGpuId(),

                            prediction == null
                                    ? "-"
                                    : prediction.queuePosition(),

                            prediction == null
                                    ? "-"
                                    : time(prediction.start()),

                            prediction == null
                                    ? "-"
                                    : time(prediction.finish()),

                            prediction == null
                                    ? "-"
                                    : prediction.remainingWait(),

                            time(job.getSubmissionTime()),
                            time(start),
                            time(finish),
                            runtime,
                            waited,
                            job.getChargedMinutes(),
                            time(job.getCancellationTime()),
                            job.getCancelledBy(),
                            job.getCancellationReason()
                    }
            );
        }

        String utilization = scheduler.getUtilization().isEmpty()
                ? "N/A"
                : String.format(
                        "%.2f%%",
                        scheduler.getUtilization().getAsDouble()
                );

        summary.setText(
                "Clock: " + time(scheduler.getNow())
                        + "    Queued: " + counts[0]
                        + "    Running: " + counts[1]
                        + "    Completed: " + counts[2]
                        + "    Cancelled: " + counts[3]
                        + "    Busy GPUs: " + counts[1] + "/4"
                        + "    Utilization: " + utilization
        );
    }

    /**
     * Starts the demo.
     *
     * If a snapshot exists, restore it.
     * Otherwise, initialize the clock using separate date and time pickers.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path file = Paths.get(
                        args.length == 0
                                ? "data/simulation.dat"
                                : args[0]
                );

                Scheduler scheduler;

                if (Files.exists(file)) {
                    scheduler = Scheduler.load(file);
                } else {
                    JSpinner date =
                            new JSpinner(new SpinnerDateModel());

                    date.setEditor(
                            new JSpinner.DateEditor(
                                    date,
                                    "dd/MM/yyyy"
                            )
                    );

                    JSpinner clock =
                            new JSpinner(new SpinnerDateModel());

                    clock.setEditor(
                            new JSpinner.DateEditor(
                                    clock,
                                    "HH:mm"
                            )
                    );

                    int answer = JOptionPane.showConfirmDialog(
                            null,
                            new Object[]{
                                    "Initial simulation date", date,
                                    "Initial simulation time", clock
                            },
                            "Initialize clock",
                            JOptionPane.OK_CANCEL_OPTION
                    );

                    if (answer != JOptionPane.OK_OPTION) {
                        return;
                    }

                    // Commit any text typed into the spinner editors.
                    date.commitEdit();
                    clock.commitEdit();

                    ZoneId zone = ZoneId.systemDefault();

                    LocalDate day = ((Date) date.getValue())
                            .toInstant()
                            .atZone(zone)
                            .toLocalDate();

                    LocalTime hour = ((Date) clock.getValue())
                            .toInstant()
                            .atZone(zone)
                            .toLocalTime();

                    scheduler = new Scheduler(
                            LocalDateTime.of(day, hour)
                    );
                }

                new SchedulerFrame(scheduler, file)
                        .setVisible(true);

            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                        null,
                        "Cannot start simulation: "
                                + exception.getMessage(),
                        "Startup error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}

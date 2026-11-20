package gpu.accounts;

import gpu.scheduling.GPU;
import gpu.scheduling.Job;
import gpu.scheduling.JobService;
import gpu.scheduling.Prediction;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

// Builds the application window and its screens.
// AccountService handles validation, permissions and saved data.
@SuppressWarnings("serial")
public final class AccountFrame extends JFrame {
    private final AccountService service;
    private final JobService jobs;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color BACKGROUND = new Color(243, 245, 249);
    private static final Color SIDEBAR = new Color(51, 68, 84);
    private static final Color BORDER = new Color(224, 230, 237);
    private static final Color TEXT = new Color(49, 61, 75);
    private static final Color MUTED = new Color(111, 124, 139);

    // Main panel whose contents change when switching screens.
    private final JPanel body =
            new JPanel(new BorderLayout(12, 12));

    private DefaultTableModel requests;
    private DefaultTableModel students;
    private DefaultTableModel dashboardJobs;
    private DefaultTableModel studentJobs;
    private DefaultTableModel waitingJobs;
    private DefaultTableModel completedJobs;
    private DefaultTableModel allJobs;
    private DefaultTableModel balances;
    private JPanel gpuCards;
    private JPanel dashboardSummary;
    private MetricCard queuedMetric;
    private MetricCard runningMetric;
    private MetricCard completedMetric;
    private MetricCard cancelledMetric;
    private MetricCard busyMetric;
    private MetricCard utilizationMetric;
    private MetricCard availableMetric;
    private MetricCard allocatedMetric;
    private MetricCard usedMetric;
    private MetricCard reservedMetric;
    private JLabel clockLabel;
    private JTextArea studentJobDetails;
    private JTextField search;
    private JButton adminCancelButton;
    private JButton studentCancelButton;
    private JButton approveRequestButton;
    private JButton customApproveRequestButton;
    private JButton rejectRequestButton;
    private JComboBox<String> requestStatusFilter;
    private JComboBox<String> studentJobStatusFilter;
    private JComboBox<String> allJobStatusFilter;
    private JTextField studentJobSearch;
    private JTextField allJobSearch;
    private TableRowSorter<DefaultTableModel> requestSorter;
    private TableRowSorter<DefaultTableModel> studentJobSorter;
    private TableRowSorter<DefaultTableModel> allJobSorter;
    private JTable requestTable;
    private JTable allJobsTable;
    private JTable studentJobsTable;
    private JTable completedJobsTable;
    private JTable waitingJobsTable;

    // Displays the selected student's information and history.
    private final JTextArea details = new JTextArea();

    public AccountFrame(AccountService service, JobService jobs) {

        super("GPU Management - Accounts and Administration");

        this.service = service;
        this.jobs = jobs;
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 15));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 15));

        // Close the application when the window is closed.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(body);
        body.setBackground(BACKGROUND);

        body.setBorder(
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        );

        setMinimumSize(new Dimension(900, 620));
        setSize(1180, 760);
        setLocationRelativeTo(null);

        // The first screen displayed is the login screen.
        showLogin();
    }

    // Represents a button action that may report an error.
    private interface Action {
        void run() throws Exception;
    }

    // Creates a button and attaches its action.
    private JButton button(String text, Action action) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setFocusPainted(true);
        button.setOpaque(true);
        if ("Advance Minute".equals(text) || "Submit job".equals(text)) {
            button.setBackground(new Color(24, 164, 139));
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(17, 135, 116)),
                    BorderFactory.createEmptyBorder(9, 14, 9, 14)
            ));
        } else {
            button.setBackground(Color.WHITE);
            button.setForeground(TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
        }

        button.addActionListener(event -> {
            try {
                action.run();

            } catch (Exception exception) {
                // Show validation and file errors in a dialog.
                String prefix = exception instanceof IOException
                        ? "File operation failed: "
                        : "";

                JOptionPane.showMessageDialog(
                        this,
                        prefix + exception.getMessage(),
                        "Unable to complete action",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        return button;
    }

    // Clears the previous screen and adds a heading.
    private void reset(String heading) {
        body.removeAll();
        body.setBackground(BACKGROUND);

        JLabel title = new JLabel(heading);
        title.setFont(
                new Font("Segoe UI", Font.BOLD, 22)
        );
        title.setForeground(TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        body.add(title, BorderLayout.NORTH);
    }

    // Updates the window after changing its contents.
    private void redraw() {
        body.revalidate();
        body.repaint();
    }

    // Creates a two-column form with labels and input fields.
    private JPanel form(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(
                new GridLayout(labels.length, 2, 12, 12)
        );
        panel.setBackground(Color.WHITE);

        for (int i = 0; i < labels.length; i++) {
            JLabel label = new JLabel(labels[i]);
            label.setForeground(TEXT);
            panel.add(label);
            panel.add(fields[i]);
        }

        return panel;
    }

    private static JPanel cardPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));
        return panel;
    }

    private static final class MetricCard extends JPanel {
        private final JLabel value;

        private MetricCard(String title, String initial, Color color) {
            super(new BorderLayout(4, 8));
            setBackground(color);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(color.darker()),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));

            JLabel heading = new JLabel(title);
            heading.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            heading.setForeground(new Color(65, 77, 91));

            value = new JLabel(initial);
            value.setFont(new Font("Segoe UI", Font.BOLD, 26));
            value.setForeground(TEXT);

            add(heading, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        private void setValue(String text) {
            value.setText(text);
        }

        private void emphasize() {
            value.setFont(new Font("Segoe UI", Font.BOLD, 32));
        }
    }

    private JButton navigationButton(String label, boolean selected) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(true);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setBackground(selected
                ? new Color(72, 94, 114)
                : SIDEBAR);
        button.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 12));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        return button;
    }

    // Displays the login form.
    private void showLogin() {
        body.removeAll();
        body.setBackground(BACKGROUND);
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BACKGROUND);

        JPanel card = cardPanel(new BorderLayout(0, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(30, 34, 24, 34)
        ));
        card.setPreferredSize(new Dimension(460, 470));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);

        JLabel heading = new JLabel("GPU Utilization Management");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 24));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("4 × NVIDIA H100 · Simulated");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(5));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(26));

        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        styleInput(username);
        styleInput(password);
        content.add(labeledField("Username / university ID", username));
        content.add(Box.createVerticalStrut(16));
        content.add(labeledField("Password", password));
        content.add(Box.createVerticalStrut(14));

        JLabel message = new JLabel(" ");
        message.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        message.setForeground(new Color(159, 73, 62));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setPreferredSize(new Dimension(380, 38));
        content.add(message);
        content.add(Box.createVerticalStrut(4));

        JButton signIn = button("Sign in", () -> {
            char[] characters = password.getPassword();
            try {
                service.login(username.getText(), new String(characters));
                if (service.getCurrentUser().getRole()
                        == Role.ADMINISTRATOR) {
                    showAdmin();
                } else {
                    showStudent();
                }
            } catch (IllegalArgumentException exception) {
                message.setText("<html>" + escapeHtml(exception.getMessage())
                        + "</html>");
                username.requestFocusInWindow();
            } finally {
                java.util.Arrays.fill(characters, '\0');
                password.setText("");
            }
        });
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        signIn.setPreferredSize(new Dimension(390, 44));
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        content.add(signIn);

        JButton register = new JButton("New student? Request an account");
        register.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        register.setForeground(new Color(36, 104, 143));
        register.setBorderPainted(false);
        register.setContentAreaFilled(false);
        register.setFocusPainted(true);
        register.setAlignmentX(Component.LEFT_ALIGNMENT);
        register.addActionListener(event -> showRegistration());
        content.add(Box.createVerticalStrut(12));
        content.add(register);

        card.add(content, BorderLayout.CENTER);
        page.add(card);
        body.add(page, BorderLayout.CENTER);
        getRootPane().setDefaultButton(signIn);
        SwingUtilities.invokeLater(() -> username.requestFocusInWindow());
        redraw();
    }

    // Displays the student registration form.
    private void showRegistration() {
        body.removeAll();
        body.setBackground(BACKGROUND);
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BACKGROUND);
        JPanel card = cardPanel(new BorderLayout(0, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(26, 34, 24, 34)
        ));
        card.setPreferredSize(new Dimension(540, 660));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        JLabel heading = new JLabel("Request a student account");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 23));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Complete the five fields below. Job details are not needed."
        );
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(18));

        JTextField id = new JTextField();
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JTextField phone = new JTextField();
        JPasswordField password = new JPasswordField();
        JComponent[] fields = {id, name, email, phone, password};
        String[] labels = {
                "University ID", "Full name", "Email",
                "Phone number", "Chosen password"
        };
        for (int i = 0; i < fields.length; i++) {
            styleInput(fields[i]);
            content.add(labeledField(labels[i], fields[i]));
            content.add(Box.createVerticalStrut(10));
        }

        JLabel message = new JLabel(" ");
        message.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        message.setForeground(new Color(159, 73, 62));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setPreferredSize(new Dimension(420, 40));
        content.add(message);

        JButton submit = button("Submit registration request", () -> {
            char[] characters = password.getPassword();
            try {
                service.register(
                        id.getText(),
                        name.getText(),
                        email.getText(),
                        phone.getText(),
                        new String(characters)
                );
                JOptionPane.showMessageDialog(
                        this,
                        "Request submitted. Wait for administrator approval.",
                        "Registration submitted",
                        JOptionPane.INFORMATION_MESSAGE
                );
                showLogin();
            } catch (IllegalArgumentException exception) {
                message.setText("<html>" + escapeHtml(exception.getMessage())
                        + "</html>");
            } finally {
                java.util.Arrays.fill(characters, '\0');
                password.setText("");
            }
        });
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setPreferredSize(new Dimension(420, 44));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JButton back = new JButton("Back to sign in");
        back.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        back.setForeground(new Color(36, 104, 143));
        back.setBorderPainted(false);
        back.setContentAreaFilled(false);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.addActionListener(event -> showLogin());
        content.add(Box.createVerticalStrut(8));
        content.add(submit);
        content.add(Box.createVerticalStrut(8));
        content.add(back);

        card.add(content, BorderLayout.CENTER);
        page.add(card);
        body.add(page, BorderLayout.CENTER);
        getRootPane().setDefaultButton(submit);
        redraw();
    }

    private static void styleInput(JComponent input) {
        input.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        input.setForeground(TEXT);
        input.setBackground(Color.WHITE);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(196, 207, 218)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        input.setPreferredSize(new Dimension(390, 42));
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    }

    private static JPanel labeledField(String label, JComponent input) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBackground(Color.WHITE);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        JLabel heading = new JLabel(label);
        heading.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(heading);
        group.add(Box.createVerticalStrut(5));
        group.add(input);
        return group;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // Creates a table model that users cannot edit directly.
    private DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // Applies shared settings to the application's tables.
    private JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);

        table.setRowHeight(34);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setForeground(TEXT);
        table.setBackground(Color.WHITE);
        table.setGridColor(BORDER);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );
        table.getTableHeader().setFont(
                new Font("Segoe UI", Font.BOLD, 14)
        );
        table.getTableHeader().setBackground(new Color(237, 241, 246));
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable source, Object value, boolean selected,
                    boolean focused, int row, int column
            ) {
                Component cell = super.getTableCellRendererComponent(
                        source, value, selected, focused, row, column
                );
                String label = value == null ? "" : value.toString();
                if (!selected && ("QUEUED".equals(label)
                        || "RUNNING".equals(label)
                        || "COMPLETED".equals(label)
                        || "CANCELLED".equals(label)
                        || "PENDING".equals(label)
                        || "APPROVED".equals(label)
                        || "REJECTED".equals(label))) {
                    setOpaque(true);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setBackground(statusBackground(label));
                    setForeground(statusForeground(label));
                } else {
                    setHorizontalAlignment(SwingConstants.LEADING);
                    setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    setBackground(selected
                            ? new Color(222, 237, 244)
                            : Color.WHITE);
                    setForeground(TEXT);
                }
                return cell;
            }
        });

        // Allows sorting by clicking column headings.
        table.setAutoCreateRowSorter(true);

        return table;
    }

    private static Color statusBackground(String status) {
        switch (status) {
            case "RUNNING":
            case "APPROVED":
                return new Color(218, 243, 235);
            case "COMPLETED":
                return new Color(222, 239, 249);
            case "CANCELLED":
            case "REJECTED":
                return new Color(253, 229, 225);
            default:
                return new Color(240, 235, 250);
        }
    }

    private static Color statusForeground(String status) {
        switch (status) {
            case "RUNNING":
            case "APPROVED":
                return new Color(28, 112, 84);
            case "COMPLETED":
                return new Color(36, 104, 143);
            case "CANCELLED":
            case "REJECTED":
                return new Color(159, 73, 62);
            default:
                return new Color(103, 83, 148);
        }
    }

    // Reads the university ID from the selected request row.
    private String selectedId(JTable table) {
        int row = table.getSelectedRow();

        if (row < 0) {
            throw new IllegalArgumentException(
                    "Select a request first."
            );
        }

        int modelRow = table.convertRowIndexToModel(row);
        return String.valueOf(table.getModel().getValueAt(modelRow, 0));
    }

    private static String[] fullJobColumns() {
        return new String[]{
                "ID", "Student", "Application", "Task size",
                "Workload units", "Estimated duration", "Status",
                "Queue position", "Predicted start", "Predicted finish",
                "Remaining wait", "GPU", "Submission time", "Actual start",
                "Actual finish", "Runtime used", "Total queue wait",
                "Allowance charged", "Result", "Cancelled at",
                "Cancelled by", "Reason"
        };
    }

    private Object[] jobRow(Job job, Prediction prediction) {
        long runtime = job.getStartTime() == null
                ? 0
                : ChronoUnit.MINUTES.between(
                        job.getStartTime(),
                        job.getFinishTime() == null
                                ? jobs.getNow() : job.getFinishTime()
                );
        Long queueWait = job.getStartTime() == null
                ? null
                : ChronoUnit.MINUTES.between(
                        job.getSubmissionTime(), job.getStartTime()
                );

        return new Object[]{
                job.getId(),
                job.getStudentId(),
                job.getApplication(),
                job.getTaskSize(),
                job.getWorkloadUnits(),
                job.getDuration() + " min",
                job.getStatus(),
                prediction == null ? "-" : prediction.queuePosition(),
                prediction == null ? "-" : time(prediction.start()),
                prediction == null ? "-" : time(prediction.finish()),
                prediction == null ? "-" : prediction.remainingWait(),
                job.getGpuId() == null ? "-" : job.getGpuId(),
                time(job.getSubmissionTime()),
                time(job.getStartTime()),
                time(job.getFinishTime()),
                runtime,
                queueWait == null ? "-" : queueWait,
                job.getChargedMinutes(),
                job.getResult() == null ? "-" : job.getResult(),
                time(job.getCancellationTime()),
                job.getCancelledBy() == null ? "-" : job.getCancelledBy(),
                job.getCancellationReason() == null
                        ? "-" : job.getCancellationReason()
        };
    }

    private static void updateTableFilter(
            TableRowSorter<DefaultTableModel> sorter,
            JTextField searchField,
            JComboBox<String> statusFilter,
            int statusColumn,
            int... searchColumns
    ) {
        List<RowFilter<DefaultTableModel, Integer>> filters =
                new java.util.ArrayList<>();

        String query = searchField.getText().trim();
        if (!query.isEmpty()) {
            String escaped = "(?i)" + java.util.regex.Pattern.quote(query);
            List<RowFilter<DefaultTableModel, Integer>> searchFilters =
                    new java.util.ArrayList<>();
            for (int column : searchColumns) {
                searchFilters.add(RowFilter.regexFilter(escaped, column));
            }
            filters.add(RowFilter.orFilter(searchFilters));
        }

        String status = String.valueOf(statusFilter.getSelectedItem());
        if (!"All statuses".equals(status)) {
            filters.add(RowFilter.regexFilter(
                    "^" + java.util.regex.Pattern.quote(status) + "$",
                    statusColumn
            ));
        }
        sorter.setRowFilter(filters.isEmpty()
                ? null : RowFilter.andFilter(filters));
    }

    private boolean selectedRequestIsPending(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return false;
        }
        int row = table.convertRowIndexToModel(selected);
        return "PENDING".equals(
                String.valueOf(table.getModel().getValueAt(row, 4))
        );
    }

    // Asks the user to enter a whole number of minutes.
    private Integer minutes(String prompt, int initial) {
        String value = JOptionPane.showInputDialog(
                this, prompt, initial
        );

        if (value == null) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Enter a whole number of minutes."
            );
        }
    }

    // Collects the administrator's reason for an allowance change.
    private String reason() {
        return JOptionPane.showInputDialog(
                this,
                "Reason for changing the allowance:"
        );
    }

    // Builds the administrator overview and management screens.
    private void showAdmin() throws IOException {
        reset("Administrator dashboard");

        // Review all requests while filtering decisions by status.
        requests = model(
                "University ID", "Name", "Email", "Phone", "Status"
        );

        requestTable = table(requests);
        requestSorter = new TableRowSorter<>(requests);
        requestTable.setRowSorter(requestSorter);

        JPanel         requestPanel =
                cardPanel(new BorderLayout(10, 10));
        JPanel requestHeader = new JPanel(new BorderLayout(8, 8));
        requestHeader.setBackground(Color.WHITE);
        requestHeader.add(new JLabel("Registration requests and decisions"),
                BorderLayout.NORTH);
        JPanel requestFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        requestFilterPanel.setBackground(Color.WHITE);
        requestFilterPanel.add(new JLabel("Status:"));
        requestStatusFilter = new JComboBox<>(new String[]{
                "All statuses", "PENDING", "APPROVED", "REJECTED"
        });
        requestStatusFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        requestFilterPanel.add(requestStatusFilter);
        requestHeader.add(requestFilterPanel, BorderLayout.SOUTH);
        requestPanel.add(requestHeader, BorderLayout.NORTH);

        requestPanel.add(
                new JScrollPane(requestTable),
                BorderLayout.CENTER
        );

        JPanel requestActions = new JPanel();
        requestActions.setBackground(Color.WHITE);

        // Approve the selected request with the default allowance.
        approveRequestButton = button("Approve (100 minutes)", () -> {
                    service.approve(selectedId(requestTable));
                    refreshAdmin();
                    refreshDashboard();
                });
        approveRequestButton.setEnabled(false);
        requestActions.add(approveRequestButton);

        // Approve the selected request with an entered allowance.
        customApproveRequestButton =
                button("Approve with custom minutes", () -> {
                    String id = selectedId(requestTable);

                    Integer allocation = minutes(
                            "Initial allocation in minutes:", 100
                    );
                    if (allocation == null) {
                        return;
                    }

                    service.approve(id, allocation);
                    refreshAdmin();
                    refreshDashboard();
                });
        customApproveRequestButton.setEnabled(false);
        requestActions.add(customApproveRequestButton);

        rejectRequestButton = button("Reject", () -> {
            service.reject(selectedId(requestTable));
            refreshAdmin();
            refreshDashboard();
        });
        rejectRequestButton.setEnabled(false);
        requestActions.add(rejectRequestButton);

        requestActions.add(
                button("Refresh", this::refreshAdmin)
        );

        requestPanel.add(requestActions, BorderLayout.SOUTH);
        requestStatusFilter.addActionListener(event -> {
            String status = String.valueOf(
                    requestStatusFilter.getSelectedItem()
            );
            requestSorter.setRowFilter("All statuses".equals(status)
                    ? null
                    : RowFilter.regexFilter(
                            "^" + java.util.regex.Pattern.quote(status) + "$",
                            4
                    ));
        });
        requestTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                boolean pending = selectedRequestIsPending(requestTable);
                approveRequestButton.setEnabled(pending);
                rejectRequestButton.setEnabled(pending);
                customApproveRequestButton.setEnabled(pending);
            }
        });

        // Student lookup and allowance management.
        students = model(
                "University ID", "Name", "Email", "Phone",
                "Allocated", "Used", "Reserved", "Available"
        );

        JTable studentTable = table(students);
        TableRowSorter<DefaultTableModel> studentSorter =
                new TableRowSorter<>(students);
        studentTable.setRowSorter(studentSorter);
        search = new JTextField(16);

        JPanel searchBar =
                new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchBar.setBackground(Color.WHITE);

        searchBar.add(new JLabel("University ID:"));
        searchBar.add(search);

        searchBar.add(button("Search ID", () -> {
            String id = search.getText().trim();
            studentSorter.setRowFilter(id.isEmpty()
                    ? null
                    : RowFilter.regexFilter(
                            "(?i)^" + java.util.regex.Pattern.quote(id) + "$",
                            0
                    ));
            if (id.isEmpty()) {
                details.setText("");
            } else {
                showStudentDetails();
            }
        }));

        searchBar.add(
                button("View student", this::showStudentDetails)
        );

        searchBar.add(button("Grant extra time", () -> {
            String id = search.getText().trim();
            Integer value = minutes("Additional minutes:", 30);
            if (value == null) {
                return;
            }
            String note = reason();
            if (note == null) {
                return;
            }
            if (note.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "A reason for the allowance change is required."
                );
            }

            service.grantExtraTime(id, value, note);

            refreshAdmin();
            refreshDashboard();
            showStudentDetails();
        }));

        searchBar.add(button("Set total allocation", () -> {
            String id = search.getText().trim();

            Integer value = minutes(
                    "New TOTAL allocation:",
                    service.getAllowance(id).getAllocated()
            );
            if (value == null) {
                return;
            }

            String note = reason();
            if (note == null) {
                return;
            }
            if (note.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "A reason for the allowance change is required."
                );
            }
            service.setAllocation(id, value, note);

            refreshAdmin();
            refreshDashboard();
            showStudentDetails();
        }));

        // Selecting a student copies their university ID into the search field.
        studentTable.getSelectionModel()
                .addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()
                            && studentTable.getSelectedRow() >= 0) {
                        int row = studentTable.convertRowIndexToModel(
                                studentTable.getSelectedRow()
                        );

                        search.setText(
                                String.valueOf(
                                        studentTable.getModel().getValueAt(row, 0)
                                )
                        );
                        try {
                            showStudentDetails();
                        } catch (IOException exception) {
                            JOptionPane.showMessageDialog(
                                    this,
                                    "File operation failed: "
                                            + exception.getMessage(),
                                    "Unable to load student",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    }
                });

        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        // The upper section shows students; the lower section shows details.
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(studentTable),
                new JScrollPane(details)
        );
        split.setBorder(BorderFactory.createEmptyBorder());

        split.setResizeWeight(0.55);

        JPanel studentPanel = cardPanel(new BorderLayout(8, 8));
        JPanel studentHeader = new JPanel(new BorderLayout(4, 6));
        studentHeader.setBackground(Color.WHITE);
        studentHeader.add(new JLabel("Students, balances and job history"),
                BorderLayout.NORTH);
        studentHeader.add(searchBar, BorderLayout.SOUTH);
        studentPanel.add(studentHeader, BorderLayout.NORTH);
        studentPanel.add(split, BorderLayout.CENTER);
        studentPanel.add(new JLabel("Select a student to inspect their account"),
                BorderLayout.SOUTH);

        queuedMetric = new MetricCard("Queued jobs", "0",
                new Color(236, 230, 250));
        runningMetric = new MetricCard("Running jobs", "0",
                new Color(217, 240, 233));
        completedMetric = new MetricCard("Completed jobs", "0",
                new Color(220, 239, 249));
        cancelledMetric = new MetricCard("Cancelled jobs", "0",
                new Color(251, 229, 224));
        busyMetric = new MetricCard("Busy GPUs", "0 / 4",
                new Color(224, 238, 247));
        utilizationMetric = new MetricCard("Utilization", "N/A",
                new Color(250, 239, 218));
        dashboardSummary = new JPanel(new GridLayout(0, 3, 10, 10));
        dashboardSummary.setBackground(BACKGROUND);
        dashboardSummary.add(queuedMetric);
        dashboardSummary.add(runningMetric);
        dashboardSummary.add(completedMetric);
        dashboardSummary.add(cancelledMetric);
        dashboardSummary.add(busyMetric);
        dashboardSummary.add(utilizationMetric);

        gpuCards = new JPanel(new GridLayout(1, 4, 12, 0));
        gpuCards.setBackground(BACKGROUND);
        dashboardJobs = model(
                "ID", "Student", "Application", "Status", "Queue position",
                "Predicted start", "Predicted finish", "Remaining wait"
        );
        waitingJobs = model(
                "ID", "Student", "Application", "Status", "Queue position",
                "Predicted start", "Predicted finish", "Remaining wait"
        );
        completedJobs = model(fullJobColumns());
        allJobs = model(fullJobColumns());
        balances = model(
                "University ID", "Student name", "Allocated minutes",
                "Used minutes", "Reserved minutes", "Available minutes"
        );

        JTable dashboardJobTable = table(dashboardJobs);
        dashboardJobTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        waitingJobsTable = table(waitingJobs);
        waitingJobsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        completedJobsTable = table(completedJobs);
        completedJobsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        allJobsTable = table(allJobs);
        allJobsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JTable balancesTable = table(balances);
        balancesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        allJobSorter = new TableRowSorter<>(allJobs);
        allJobsTable.setRowSorter(allJobSorter);

        JPanel dashboardControls = cardPanel(new FlowLayout(FlowLayout.LEFT));
        dashboardControls.setBackground(Color.WHITE);
        clockLabel = new JLabel("Simulation time: " + time(jobs.getNow()));
        clockLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        dashboardControls.add(clockLabel);
        dashboardControls.add(button(
                "Advance Minute",
                () -> {
                    jobs.advanceMinute();
                    refreshDashboard();
                    refreshAdmin();
                }
        ));
        adminCancelButton = button(
                "Cancel selected job",
                () -> {
                    cancelSelectedJob(dashboardJobTable);
                    refreshDashboard();
                    refreshAdmin();
                }
        );
        adminCancelButton.setEnabled(false);
        dashboardControls.add(adminCancelButton);
        dashboardControls.add(button(
                "Refresh dashboard",
                this::refreshDashboard
        ));

        dashboardJobTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                adminCancelButton.setEnabled(
                        selectedJobCanBeCancelled(dashboardJobTable)
                );
            }
        });

        JPanel gpuPanel = cardPanel(new BorderLayout(8, 8));
        gpuPanel.add(new JLabel("Four GPU devices"), BorderLayout.NORTH);
        gpuPanel.add(gpuCards, BorderLayout.CENTER);

        JPanel balancePanel = cardPanel(new BorderLayout());
        balancePanel.add(new JLabel("Student available balances"),
                BorderLayout.NORTH);
        balancePanel.add(new JScrollPane(balancesTable), BorderLayout.CENTER);

        JPanel jobPanel = cardPanel(new BorderLayout(8, 8));
        jobPanel.add(new JLabel("Waiting jobs · predicted schedule"),
                BorderLayout.NORTH);
        jobPanel.add(new JScrollPane(dashboardJobTable), BorderLayout.CENTER);

        JPanel waitingPanel = cardPanel(new BorderLayout(8, 8));
        waitingPanel.add(new JLabel(
                "Waiting queue · FCFS order and predicted start/finish times"
        ), BorderLayout.NORTH);
        waitingPanel.add(new JScrollPane(waitingJobsTable), BorderLayout.CENTER);

        JPanel completedPanel = cardPanel(new BorderLayout(8, 8));
        completedPanel.add(new JLabel(
                "Completed job records · actual execution and allowance charges"
        ), BorderLayout.NORTH);
        completedPanel.add(new JScrollPane(completedJobsTable),
                BorderLayout.CENTER);

        allJobSearch = new JTextField(18);
        allJobStatusFilter = new JComboBox<>(new String[]{
                "All statuses", "QUEUED", "RUNNING", "COMPLETED", "CANCELLED"
        });
        allJobSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        allJobStatusFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JPanel allJobFilters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        allJobFilters.setBackground(Color.WHITE);
        allJobFilters.add(new JLabel("Search ID, student, or application:"));
        allJobFilters.add(allJobSearch);
        allJobFilters.add(button("Search", this::updateAllJobFilter));
        allJobFilters.add(new JLabel("Status:"));
        allJobFilters.add(allJobStatusFilter);
        JButton allJobCancel = button("Cancel selected job", () -> {
            cancelSelectedJob(allJobsTable);
            refreshDashboard();
            refreshAdmin();
        });
        allJobCancel.setEnabled(false);
        allJobFilters.add(allJobCancel);
        JPanel allJobPanel = cardPanel(new BorderLayout(8, 8));
        allJobPanel.add(allJobFilters, BorderLayout.NORTH);
        allJobPanel.add(new JScrollPane(allJobsTable), BorderLayout.CENTER);
        allJobSorter.addRowSorterListener(event ->
                allJobCancel.setEnabled(
                        selectedJobCanBeCancelled(allJobsTable)
                )
        );
        allJobsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                allJobCancel.setEnabled(
                        selectedJobCanBeCancelled(allJobsTable)
                );
            }
        });
        allJobSearch.addActionListener(event -> updateAllJobFilter());
        allJobStatusFilter.addActionListener(event -> updateAllJobFilter());

        JPanel dashboardBody = new JPanel();
        dashboardBody.setLayout(new BoxLayout(dashboardBody, BoxLayout.Y_AXIS));
        dashboardBody.setBackground(BACKGROUND);
        dashboardBody.add(dashboardControls);
        dashboardBody.add(Box.createVerticalStrut(10));
        dashboardBody.add(dashboardSummary);
        dashboardBody.add(Box.createVerticalStrut(10));
        dashboardBody.add(gpuPanel);
        dashboardBody.add(Box.createVerticalStrut(10));
        dashboardBody.add(balancePanel);
        dashboardBody.add(Box.createVerticalStrut(10));
        dashboardBody.add(jobPanel);

        JPanel dashboard = new JPanel(new BorderLayout());
        dashboard.setBackground(BACKGROUND);
        dashboard.add(new JScrollPane(dashboardBody), BorderLayout.CENTER);

        CardLayout screenLayout = new CardLayout();
        JPanel screens = new JPanel(screenLayout);
        screens.setBackground(BACKGROUND);
        screens.add(dashboard, "overview");
        screens.add(waitingPanel, "waiting");
        screens.add(completedPanel, "completed");
        screens.add(allJobPanel, "all-jobs");
        screens.add(requestPanel, "requests");
        screens.add(studentPanel, "students");

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(200, 0));
        JLabel brand = new JLabel("GPU MANAGER");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 16));
        brand.setForeground(Color.WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(20, 16, 22, 8));
        sidebar.add(brand);

        JButton overviewNav = navigationButton("Overview", true);
        JButton waitingNav = navigationButton("Waiting jobs", false);
        JButton completedNav = navigationButton("Completed jobs", false);
        JButton allJobsNav = navigationButton("All jobs", false);
        JButton requestsNav = navigationButton("Registration requests", false);
        JButton studentsNav = navigationButton("Students & allowances", false);
        for (JButton nav : new JButton[]{
                overviewNav, waitingNav, completedNav, allJobsNav,
                requestsNav, studentsNav
        }) {
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            sidebar.add(nav);
        }

        overviewNav.addActionListener(event -> {
            screenLayout.show(screens, "overview");
            selectNavigation(overviewNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });
        waitingNav.addActionListener(event -> {
            screenLayout.show(screens, "waiting");
            selectNavigation(waitingNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });
        completedNav.addActionListener(event -> {
            screenLayout.show(screens, "completed");
            selectNavigation(completedNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });
        allJobsNav.addActionListener(event -> {
            screenLayout.show(screens, "all-jobs");
            selectNavigation(allJobsNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });
        requestsNav.addActionListener(event -> {
            screenLayout.show(screens, "requests");
            selectNavigation(requestsNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });
        studentsNav.addActionListener(event -> {
            screenLayout.show(screens, "students");
            selectNavigation(studentsNav, overviewNav, waitingNav,
                    completedNav, allJobsNav, requestsNav, studentsNav);
        });

        sidebar.add(Box.createVerticalGlue());
        JButton logout = navigationButton("Log out", false);
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        logout.addActionListener(event -> {
            service.logout();
            showLogin();
        });
        sidebar.add(logout);

        JPanel shell = new JPanel(new BorderLayout(12, 0));
        shell.setBackground(BACKGROUND);
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(screens, BorderLayout.CENTER);
        body.add(shell, BorderLayout.CENTER);

        // Fill both tables with current account data.
        refreshAdmin();
        refreshDashboard();
        redraw();
    }

    // Reloads request and student tables after changes.
    private void refreshAdmin() throws IOException {
        requests.setRowCount(0);

        for (AccessRequest request : service.getRequests()) {
            Student student = request.getApplicant();

            requests.addRow(new Object[]{
                    student.getUniversityId(),
                    student.getName(),
                    student.getEmail(),
                    student.getPhone(),
                    request.getStatus()
            });
        }
        approveRequestButton.setEnabled(
                selectedRequestIsPending(requestTable)
        );
        customApproveRequestButton.setEnabled(
                selectedRequestIsPending(requestTable)
        );
        rejectRequestButton.setEnabled(
                selectedRequestIsPending(requestTable)
        );

        students.setRowCount(0);
        balances.setRowCount(0);

        for (Student student : service.getStudents()) {
            TimeAllowance allowance =
                    service.getAllowance(student.getUniversityId());

            students.addRow(new Object[]{
                    student.getUniversityId(),
                    student.getName(),
                    student.getEmail(),
                    student.getPhone(),
                    allowance.getAllocated(),
                    allowance.getUsed(),
                    allowance.getReserved(),
                    allowance.getAvailable()
            });
            balances.addRow(new Object[]{
                    student.getUniversityId(),
                    student.getName(),
                    allowance.getAllocated(),
                    allowance.getUsed(),
                    allowance.getReserved(),
                    allowance.getAvailable()
            });
        }
    }

    private void updateStudentJobFilter() {
        updateTableFilter(studentJobSorter, studentJobSearch,
                studentJobStatusFilter, 6, 0, 1);
    }

    private void refreshDashboard() throws IOException {
        Map<String, Prediction> predictions = jobs.getPredictions();
        long[] counts = new long[Job.Status.values().length];

        dashboardJobs.setRowCount(0);
        waitingJobs.setRowCount(0);
        completedJobs.setRowCount(0);
        allJobs.setRowCount(0);
        gpuCards.removeAll();

        for (GPU gpu : jobs.getGpus()) {
            JPanel card = cardPanel(new GridLayout(0, 1, 3, 3));
            JLabel title = new JLabel("GPU " + gpu.id());
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(TEXT);
            JLabel availability = new JLabel(
                    gpu.isAvailable() ? "Available" : "Busy"
            );
            availability.setForeground(gpu.isAvailable()
                    ? new Color(28, 112, 84)
                    : new Color(103, 83, 148));
            card.add(title);
            card.add(availability);
            card.add(new JLabel("Current job: "
                    + (gpu.jobId() == null ? "-" : gpu.jobId())));
            card.add(new JLabel("Remaining runtime: "
                    + gpu.remainingMinutes() + " min"));
            card.add(new JLabel("Expected finish: "
                    + time(gpu.expectedFinish())));
            gpuCards.add(card);
        }
        gpuCards.revalidate();
        gpuCards.repaint();

        for (Job job : jobs.getJobs()) {
            counts[job.getStatus().ordinal()]++;
            Prediction prediction = predictions.get(job.getId());
            allJobs.addRow(jobRow(job, prediction));
            if (job.getStatus() == Job.Status.COMPLETED) {
                completedJobs.addRow(jobRow(job, prediction));
            }
            if (job.getStatus() == Job.Status.QUEUED) {
                Object[] waitingRow = {
                        job.getId(),
                        job.getStudentId(),
                        job.getApplication(),
                        job.getStatus(),
                        prediction == null ? "-" : prediction.queuePosition(),
                        prediction == null ? "-" : time(prediction.start()),
                        prediction == null ? "-" : time(prediction.finish()),
                        prediction == null ? "-" : prediction.remainingWait()
                };
                dashboardJobs.addRow(waitingRow);
                waitingJobs.addRow(waitingRow);
            }
        }

        String utilization = jobs.getUtilization().isEmpty()
                ? "N/A"
                : String.format("%.2f%%",
                        jobs.getUtilization().getAsDouble());
        queuedMetric.setValue(Long.toString(counts[0]));
        runningMetric.setValue(Long.toString(counts[1]));
        completedMetric.setValue(Long.toString(counts[2]));
        cancelledMetric.setValue(Long.toString(counts[3]));
        busyMetric.setValue(counts[1] + " / 4");
        utilizationMetric.setValue(utilization);
        clockLabel.setText("Simulation time: " + time(jobs.getNow()));
        updateAllJobFilter();
    }

    private void updateAllJobFilter() {
        updateTableFilter(allJobSorter, allJobSearch,
                allJobStatusFilter, 6, 0, 1, 2);
    }

    private void selectNavigation(
            JButton selected,
            JButton... navigation
    ) {
        for (JButton button : navigation) {
            button.setBackground(button == selected
                    ? new Color(72, 94, 114)
                    : SIDEBAR);
        }
    }

    private void cancelSelectedJob(JTable table) throws IOException {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            throw new IllegalArgumentException("Select a job first.");
        }

        int row = table.convertRowIndexToModel(selected);
        String jobId = String.valueOf(table.getModel().getValueAt(row, 0));
        Job selectedJob = jobs.getJobs().stream()
                .filter(job -> job.getId().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Job not found."
                ));
        if (selectedJob.getStatus() != Job.Status.QUEUED
                && selectedJob.getStatus() != Job.Status.RUNNING) {
            throw new IllegalStateException(
                    "Only queued or running jobs can be cancelled."
            );
        }

        int charge = selectedJob.getStatus() == Job.Status.RUNNING
                ? (int) ChronoUnit.MINUTES.between(
                        selectedJob.getStartTime(), jobs.getNow()
                )
                : 0;
        String cancellation = selectedJob.getStatus() == Job.Status.QUEUED
                ? "This queued job will be charged 0 GPU minutes."
                : "This running job will be charged " + charge
                        + " elapsed GPU minutes.";
        String reason = JOptionPane.showInputDialog(
                this,
                "Reason for cancelling " + jobId + ":"
        );
        if (reason == null) {
            return;
        }
        if (reason.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "A cancellation reason is required."
            );
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                cancellation + "\nThe full "
                        + selectedJob.getDuration()
                        + "-minute reservation will be released.\n"
                        + "Recorded reason: " + reason.trim() + "\n"
                        + "Continue with cancellation?",
                "Confirm job cancellation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        jobs.cancelJob(jobId, reason);
    }

    private boolean selectedJobCanBeCancelled(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return false;
        }

        int statusColumn = table.getColumnModel().getColumnIndex("Status");
        int row = table.convertRowIndexToModel(selected);
        Object value = table.getModel().getValueAt(row, statusColumn);
        return Job.Status.QUEUED.toString().equals(String.valueOf(value))
                || Job.Status.RUNNING.toString().equals(String.valueOf(value));
    }

    private static String time(LocalDateTime value) {
        return value == null ? "-" : TIME.format(value);
    }

    // Builds the text shown in the student information panel.
    private String studentDetails(String id) throws IOException {
        Student student = service.findStudent(id);
        TimeAllowance allowance = service.getAllowance(id);

        StringBuilder text = new StringBuilder();

        text.append(student.getName())
                .append(" | ").append(id).append("\n")
                .append(student.getEmail())
                .append(" | ").append(student.getPhone())
                .append("\n\n")
                .append("Allocated: ").append(allowance.getAllocated())
                .append(" | Used: ").append(allowance.getUsed())
                .append(" | Reserved: ").append(allowance.getReserved())
                .append(" | Available: ").append(allowance.getAvailable())
                .append(" minutes\n\nJobs:\n");

        Map<String, Prediction> predictions = jobs.getPredictions();
        for (Job job : jobs.getJobs()) {
            if (!job.getStudentId().equals(id)) {
                continue;
            }

            Prediction prediction = predictions.get(job.getId());
            text.append(job.getId())
                    .append(" | ").append(job.getTitle())
                    .append(" | ").append(job.getTaskSize())
                    .append(" | ").append(job.getWorkloadUnits())
                    .append(" workload units / ")
                    .append(job.getDuration()).append(" min")
                    .append(" | ").append(job.getStatus())
                    .append(" | GPU ")
                    .append(job.getGpuId() == null ? "-" : job.getGpuId())
                    .append(" | submitted ").append(time(job.getSubmissionTime()));

            if (prediction != null) {
                text.append(" | predicted ")
                        .append(time(prediction.start()))
                        .append(" | wait ")
                        .append(prediction.remainingWait())
                        .append(" min");
            }
            if (job.getStartTime() != null) {
                text.append(" | started ").append(time(job.getStartTime()));
            }
            if (job.getFinishTime() != null) {
                text.append(" | finished ").append(time(job.getFinishTime()));
            }
            text.append(" | charged ")
                    .append(job.getChargedMinutes()).append(" min")
                    .append(" | result ")
                    .append(job.getResult() == null ? "-" : job.getResult());
            if (job.getCancellationTime() != null) {
                text.append(" | cancelled ")
                        .append(time(job.getCancellationTime()))
                        .append(" by ")
                        .append(job.getCancelledBy())
                        .append(" | reason ")
                        .append(job.getCancellationReason());
            }
            text.append("\n");
        }

        text.append("\nAllowance changes:\n");

        for (AllowanceChange record : service.getChanges(id)) {
            text.append(record).append("\n");
        }

        return text.toString();
    }

    // Displays the student whose ID was entered in the search field.
    private void showStudentDetails() throws IOException {
        details.setText(
                studentDetails(search.getText().trim())
        );

        // Start displaying the text from the beginning.
        details.setCaretPosition(0);
    }

    // Displays information belonging to the signed-in student.
    private void showStudent() throws IOException {
        reset("Student | " + service.getCurrentUser().getName());

        availableMetric = new MetricCard(
                "Available GPU minutes", "0", new Color(217, 240, 233)
        );
        availableMetric.emphasize();
        allocatedMetric = new MetricCard(
                "Allocated", "0", new Color(220, 239, 249)
        );
        usedMetric = new MetricCard(
                "Used", "0", new Color(236, 230, 250)
        );
        reservedMetric = new MetricCard(
                "Reserved", "0", new Color(250, 239, 218)
        );
        JPanel balanceCards = new JPanel(new GridLayout(1, 4, 12, 0));
        balanceCards.setBackground(BACKGROUND);
        balanceCards.add(availableMetric);
        balanceCards.add(allocatedMetric);
        balanceCards.add(usedMetric);
        balanceCards.add(reservedMetric);

        studentJobs = model(
                "ID", "Title", "Application", "Task size",
                "Workload units", "Estimated duration", "Status", "GPU",
                "Predicted start", "Predicted finish", "Wait left",
                "Submission time", "Actual start", "Actual finish",
                "Runtime used", "Total queue wait", "Charge", "Result",
                "Cancelled at", "Cancelled by", "Cancellation reason"
        );
        JTable table = table(studentJobs);
        studentJobsTable = table;
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableRowSorter<DefaultTableModel> sorter =
                new TableRowSorter<>(studentJobs);
        table.setRowSorter(sorter);
        studentJobSorter = sorter;

        studentJobSearch = new JTextField(18);
        JButton searchButton = new JButton("Search jobs");
        studentJobStatusFilter = new JComboBox<>(new String[]{
                "All statuses", "QUEUED", "RUNNING", "COMPLETED", "CANCELLED"
        });
        studentJobStatusFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        studentJobSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchButton.addActionListener(event -> updateStudentJobFilter());
        studentJobSearch.addActionListener(event -> updateStudentJobFilter());
        studentJobStatusFilter.addActionListener(
                event -> updateStudentJobFilter()
        );

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchBar.add(new JLabel("Search own jobs:"));
        searchBar.add(studentJobSearch);
        searchBar.add(new JLabel("Status:"));
        searchBar.add(studentJobStatusFilter);
        searchBar.add(searchButton);
        studentCancelButton = button("Cancel selected job", () -> {
            cancelSelectedJob(table);
            refreshStudent();
        });
        studentCancelButton.setEnabled(false);
        searchBar.add(studentCancelButton);

        studentJobDetails = new JTextArea(5, 40);
        studentJobDetails.setEditable(false);
        studentJobDetails.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        studentJobDetails.setLineWrap(true);
        studentJobDetails.setWrapStyleWord(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedJobDetails(table);
            }
        });

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                new JScrollPane(studentJobDetails)
        );
        split.setResizeWeight(0.75);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(searchBar, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.add(button("Submit job", this::submitStudentJob));
        actions.add(button("Refresh", this::refreshStudent));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BACKGROUND);
        top.add(balanceCards, BorderLayout.NORTH);
        top.add(actions, BorderLayout.SOUTH);

        JPanel studentSidebar = new JPanel();
        studentSidebar.setLayout(new BoxLayout(studentSidebar, BoxLayout.Y_AXIS));
        studentSidebar.setBackground(SIDEBAR);
        studentSidebar.setPreferredSize(new Dimension(200, 0));
        JLabel brand = new JLabel("GPU MANAGER");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 16));
        brand.setForeground(Color.WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(20, 16, 22, 8));
        studentSidebar.add(brand);
        JButton overviewNav = navigationButton("Overview", true);
        JButton jobsNav = navigationButton("My jobs", false);
        for (JButton nav : new JButton[]{overviewNav, jobsNav}) {
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            studentSidebar.add(nav);
        }
        studentSidebar.add(Box.createVerticalGlue());
        JButton logout = navigationButton("Log out", false);
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        logout.addActionListener(event -> {
            service.logout();
            showLogin();
        });
        studentSidebar.add(logout);

        JPanel overviewPanel = new JPanel(new BorderLayout(10, 10));
        overviewPanel.setBackground(BACKGROUND);
        overviewPanel.add(top, BorderLayout.NORTH);
        JLabel overviewHint = new JLabel(
                "Your available balance includes minutes reserved for accepted jobs."
        );
        overviewHint.setForeground(MUTED);
        overviewHint.setBorder(BorderFactory.createEmptyBorder(12, 4, 4, 4));
        overviewPanel.add(overviewHint, BorderLayout.CENTER);

        JPanel jobsPanel = new JPanel(new BorderLayout());
        jobsPanel.setBackground(BACKGROUND);
        jobsPanel.add(center, BorderLayout.CENTER);

        CardLayout studentLayout = new CardLayout();
        JPanel studentContent = new JPanel(studentLayout);
        studentContent.setBackground(BACKGROUND);
        studentContent.add(overviewPanel, "overview");
        studentContent.add(jobsPanel, "jobs");

        overviewNav.addActionListener(event -> {
            selectNavigation(overviewNav, overviewNav, jobsNav);
            studentLayout.show(studentContent, "overview");
        });
        jobsNav.addActionListener(event -> {
            selectNavigation(jobsNav, overviewNav, jobsNav);
            studentLayout.show(studentContent, "jobs");
            table.requestFocusInWindow();
            if (table.getRowCount() > 0) {
                table.scrollRectToVisible(table.getCellRect(0, 0, true));
            }
        });

        JPanel shell = new JPanel(new BorderLayout(12, 0));
        shell.setBackground(BACKGROUND);
        shell.add(studentSidebar, BorderLayout.WEST);
        shell.add(studentContent, BorderLayout.CENTER);
        body.add(shell, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                studentCancelButton.setEnabled(
                        selectedJobCanBeCancelled(table)
                );
            }
        });

        refreshStudent();
        redraw();
    }

    private void refreshStudent() throws IOException {
        String id = service.getCurrentUser().getUsername();
        TimeAllowance allowance = service.getAllowance(id);
        availableMetric.setValue(Integer.toString(allowance.getAvailable()));
        allocatedMetric.setValue(Integer.toString(allowance.getAllocated()));
        usedMetric.setValue(Integer.toString(allowance.getUsed()));
        reservedMetric.setValue(Integer.toString(allowance.getReserved()));

        Map<String, Prediction> predictions = jobs.getPredictions();
        studentJobs.setRowCount(0);
        for (Job job : jobs.getJobs()) {
            if (!job.getStudentId().equals(id)) {
                continue;
            }

            Prediction prediction = predictions.get(job.getId());
            long runtime = job.getStartTime() == null
                    ? 0
                    : java.time.temporal.ChronoUnit.MINUTES.between(
                            job.getStartTime(),
                            job.getFinishTime() == null
                                    ? jobs.getNow()
                                    : job.getFinishTime()
                    );
            Long queueWait = job.getStartTime() == null
                    ? null
                    : java.time.temporal.ChronoUnit.MINUTES.between(
                            job.getSubmissionTime(), job.getStartTime()
                    );

            studentJobs.addRow(new Object[]{
                    job.getId(),
                    job.getTitle(),
                    job.getApplication(),
                    job.getTaskSize(),
                    job.getWorkloadUnits(),
                    job.getDuration() + " min",
                    job.getStatus(),
                    job.getGpuId() == null ? "-" : job.getGpuId(),
                    prediction == null ? "-" : time(prediction.start()),
                    prediction == null ? "-" : time(prediction.finish()),
                    prediction == null ? "-" : prediction.remainingWait(),
                    time(job.getSubmissionTime()),
                    time(job.getStartTime()),
                    time(job.getFinishTime()),
                    runtime,
                    queueWait == null ? "-" : queueWait,
                    job.getChargedMinutes(),
                    job.getResult() == null ? "-" : job.getResult(),
                    time(job.getCancellationTime()),
                    job.getCancelledBy() == null ? "-" : job.getCancelledBy(),
                    job.getCancellationReason() == null
                            ? "-" : job.getCancellationReason()
            });
        }
        studentCancelButton.setEnabled(
                selectedJobCanBeCancelled(studentJobsTable)
        );
        updateStudentJobFilter();
    }

    private void submitStudentJob() throws IOException {
        JTextField title = new JTextField();
        JTextField application = new JTextField();
        JTextField description = new JTextField();
        JComboBox<String> size = new JComboBox<>(new String[]{
                "Small - 5 workload units / 5 minutes",
                "Medium - 15 workload units / 15 minutes",
                "Large - 30 workload units / 30 minutes",
                "Very large - 60 workload units / 60 minutes"
        });
        int[] durations = {5, 15, 30, 60};
        String studentId = service.getCurrentUser().getUsername();
        int available = service.getAllowance(studentId).getAvailable();
        JLabel balancePreview = new JLabel();
        balancePreview.setForeground(TEXT);
        balancePreview.setBorder(
                BorderFactory.createEmptyBorder(12, 4, 4, 4)
        );
        Runnable updateBalancePreview = () -> {
            int duration = durations[size.getSelectedIndex()];
            balancePreview.setText(
                    "Available now: " + available + " GPU minutes. "
                            + "This job reserves " + duration
                            + "; available after acceptance: "
                            + Math.max(0, available - duration) + "."
            );
            balancePreview.setForeground(duration > available
                    ? new Color(159, 73, 62)
                    : TEXT);
        };
        size.addActionListener(event -> updateBalancePreview.run());
        updateBalancePreview.run();

        JPanel submissionForm = new JPanel(new BorderLayout(8, 8));
        submissionForm.setBackground(Color.WHITE);
        submissionForm.add(
                form(
                        new String[]{
                                "Job title",
                                "Application name",
                                "Description",
                                "Task size"
                        },
                        new JComponent[]{title, application, description, size}
                ),
                BorderLayout.CENTER
        );
        submissionForm.add(balancePreview, BorderLayout.SOUTH);

        int answer = JOptionPane.showConfirmDialog(
                this,
                submissionForm,
                "Submit a job",
                JOptionPane.OK_CANCEL_OPTION
        );
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        int duration = durations[size.getSelectedIndex()];
        if (duration > available) {
            throw new IllegalArgumentException(
                    "Insufficient allowance: this job needs " + duration
                            + " GPU minutes, but only " + available
                            + " are available."
            );
        }
        int confirmed = JOptionPane.showConfirmDialog(
                this,
                "Duration: " + duration + " simulated GPU minutes.\n"
                        + "Available before submission: " + available + ".\n"
                        + "Available after reservation: "
                        + (available - duration) + ".\n"
                        + "Submit this job?",
                "Confirm job duration",
                JOptionPane.YES_NO_OPTION
        );
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }

        String id = jobs.submitJob(
                title.getText(),
                application.getText(),
                description.getText(),
                duration
        );
        refreshStudent();
        JOptionPane.showMessageDialog(this, "Job accepted: " + id);
    }

    private void showSelectedJobDetails(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            studentJobDetails.setText("");
            return;
        }

        int row = table.convertRowIndexToModel(selected);
        String id = String.valueOf(table.getModel().getValueAt(row, 0));
        for (Job job : jobs.getJobs()) {
            if (!job.getId().equals(id)) {
                continue;
            }

            studentJobDetails.setText(
                    job.getTitle() + " | " + job.getApplication()
                            + " | " + job.getTaskSize()
                            + " | " + job.getWorkloadUnits()
                            + " workload units / "
                            + job.getDuration() + " min"
                            + "\n" + job.getDescription()
                            + "\nStatus: " + job.getStatus()
                                    + " | Assigned GPU: "
                                    + (job.getGpuId() == null ? "-" : job.getGpuId())
                                    + " | Submitted: " + time(job.getSubmissionTime())
                                    + "\nPredicted start/finish and wait are shown in the job table."
                                    + "\nActual start: " + time(job.getStartTime())
                                    + " | Actual finish: " + time(job.getFinishTime())
                            + "\nResult: "
                            + (job.getResult() == null ? "-" : job.getResult())
                            + "\nCancellation: "
                            + (job.getCancellationReason() == null
                                    ? "-" : job.getCancellationReason())
                            + " | By: "
                            + (job.getCancelledBy() == null
                                    ? "-" : job.getCancelledBy())
                            + " | At: " + time(job.getCancellationTime())
            );
            return;
        }
    }
}

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
    private JLabel dashboardBalances;
    private JTextArea studentJobDetails;
    private JTextField search;
    private JButton adminCancelButton;
    private JButton studentCancelButton;

    // Displays the selected student's information and history.
    private final JTextArea details = new JTextArea();

    public AccountFrame(AccountService service, JobService jobs) {

        super("GPU Management - Accounts and Administration");

        this.service = service;
        this.jobs = jobs;
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 13));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 13));

        // Close the application when the window is closed.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(body);
        body.setBackground(BACKGROUND);

        body.setBorder(
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        );

        setMinimumSize(new Dimension(980, 650));
        setSize(1280, 820);
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
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setOpaque(true);
        if ("Advance Minute".equals(text) || "Submit job".equals(text)) {
            button.setBackground(new Color(24, 164, 139));
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
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
            heading.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            heading.setForeground(new Color(65, 77, 91));

            value = new JLabel(initial);
            value.setFont(new Font("Segoe UI", Font.BOLD, 24));
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
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setBackground(selected
                ? new Color(72, 94, 114)
                : SIDEBAR);
        button.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 12));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return button;
    }

    // Displays the login form.
    private void showLogin() {
        reset("GPU Management | Sign in");

        JTextField username = new JTextField(22);
        JPasswordField password = new JPasswordField(22);

        JPanel center = new JPanel(new GridBagLayout());
        JPanel box = new JPanel(new BorderLayout(12, 18));

        box.add(
                form(
                        new String[]{
                                "Username / university ID",
                                "Password"
                        },
                        new JComponent[]{username, password}
                ),
                BorderLayout.CENTER
        );

        JPanel actions = new JPanel();

        actions.add(button("Sign in", () -> {
            char[] characters = password.getPassword();

            try {
                service.login(
                        username.getText(),
                        new String(characters)
                );

            } finally {
                // Clear the entered password after the login attempt.
                java.util.Arrays.fill(characters, '\0');
                password.setText("");
            }

            // Display the screen appropriate for the signed-in user.
            if (service.getCurrentUser().getRole()
                    == Role.ADMINISTRATOR) {

                showAdmin();
            } else {
                showStudent();
            }
        }));

        actions.add(
                button("Register as a student", this::showRegistration)
        );

        box.add(actions, BorderLayout.SOUTH);
        center.add(box);

        body.add(center, BorderLayout.CENTER);

        body.add(
                new JLabel(
                        "Students sign in after administrator approval."
                ),
                BorderLayout.SOUTH
        );

        redraw();
    }

    // Displays the student registration form.
    private void showRegistration() {
        reset("Student registration");

        JTextField id = new JTextField();
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JTextField phone = new JTextField();

        JPasswordField password = new JPasswordField();

        JPanel center = new JPanel(new BorderLayout(10, 20));

        center.add(
                form(
                        new String[]{
                                "University ID",
                                "Full name",
                                "Email",
                                "Phone",
                                "Chosen password"
                        },
                        new JComponent[]{
                                id, name, email, phone, password
                        }
                ),
                BorderLayout.NORTH
        );

        JPanel actions = new JPanel();

        actions.add(button("Submit request", () -> {
            char[] characters = password.getPassword();

            try {
                // AccountService checks the inputs and saves the request.
                service.register(
                        id.getText(),
                        name.getText(),
                        email.getText(),
                        phone.getText(),
                        new String(characters)
                );

            } finally {
                java.util.Arrays.fill(characters, '\0');
                password.setText("");
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Request submitted. Wait for administrator approval."
            );

            showLogin();
        }));

        actions.add(button("Back", this::showLogin));

        body.add(center, BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);

        redraw();
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

        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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
                new Font("Segoe UI", Font.BOLD, 12)
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
                    setFont(new Font("Segoe UI", Font.PLAIN, 13));
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

    // Asks the user to enter a whole number of minutes.
    private int minutes(String prompt, int initial) {
        String value = JOptionPane.showInputDialog(
                this, prompt, initial
        );

        if (value == null) {
            throw new IllegalArgumentException("Action cancelled.");
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

        // First tab: review student registration requests.
        requests = model(
                "University ID", "Name", "Email", "Phone", "Status"
        );

        JTable requestTable = table(requests);

        JPanel         requestPanel =
                cardPanel(new BorderLayout(10, 10));
        requestPanel.add(new JLabel("Registration requests"),
                BorderLayout.NORTH);

        requestPanel.add(
                new JScrollPane(requestTable),
                BorderLayout.CENTER
        );

        JPanel requestActions = new JPanel();

        // Approve the selected request with the default allowance.
        requestActions.add(
                button("Approve (100 minutes)", () -> {
                    service.approve(selectedId(requestTable));
                    refreshAdmin();
                    refreshDashboard();
                })
        );

        // Approve the selected request with an entered allowance.
        requestActions.add(
                button("Approve with custom minutes", () -> {
                    String id = selectedId(requestTable);

                    int allocation = minutes(
                            "Initial allocation in minutes:", 100
                    );

                    service.approve(id, allocation);
                    refreshAdmin();
                    refreshDashboard();
                })
        );

        requestActions.add(button("Reject", () -> {
            service.reject(selectedId(requestTable));
            refreshAdmin();
            refreshDashboard();
        }));

        requestActions.add(
                button("Refresh", this::refreshAdmin)
        );

        requestPanel.add(requestActions, BorderLayout.SOUTH);
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
            int value = minutes("Additional minutes:", 30);
            String note = reason();
            if (note == null) {
                return;
            }

            service.grantExtraTime(id, value, note);

            refreshAdmin();
            refreshDashboard();
            showStudentDetails();
        }));

        searchBar.add(button("Set total allocation", () -> {
            String id = search.getText().trim();

            int value = minutes(
                    "New TOTAL allocation:",
                    service.getAllowance(id).getAllocated()
            );

            String note = reason();
            if (note == null) {
                return;
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
                    }
                });

        details.setEditable(false);
        details.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 13)
        );

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
        dashboardSummary = new JPanel(new GridLayout(1, 6, 10, 0));
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
                "ID", "Student", "Application", "Task size",
                "Workload units", "Estimated duration", "Status",
                "Queue position", "Predicted start",
                "Predicted finish", "Remaining wait", "GPU",
                "Submission time", "Actual start", "Actual finish",
                "Runtime used", "Total queue wait",
                "Allowance charged", "Result",
                "Cancelled at", "Cancelled by", "Reason"
        );

        JTable dashboardJobTable = table(dashboardJobs);
        dashboardJobTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

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

        dashboardBalances = new JLabel();
        dashboardBalances.setForeground(MUTED);
        JPanel balancePanel = cardPanel(new BorderLayout());
        balancePanel.add(new JLabel("Student available balances"),
                BorderLayout.NORTH);
        balancePanel.add(dashboardBalances, BorderLayout.CENTER);

        JPanel jobPanel = cardPanel(new BorderLayout(8, 8));
        jobPanel.add(new JLabel(
                "Jobs · predicted schedule and recorded execution history"
        ), BorderLayout.NORTH);
        jobPanel.add(new JScrollPane(dashboardJobTable), BorderLayout.CENTER);

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
        JButton requestsNav = navigationButton("Registration requests", false);
        JButton studentsNav = navigationButton("Students & allowances", false);
        for (JButton nav : new JButton[]{
                overviewNav, requestsNav, studentsNav
        }) {
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            sidebar.add(nav);
        }

        overviewNav.addActionListener(event -> {
            screenLayout.show(screens, "overview");
            selectNavigation(overviewNav, overviewNav, requestsNav, studentsNav);
        });
        requestsNav.addActionListener(event -> {
            screenLayout.show(screens, "requests");
            selectNavigation(requestsNav, overviewNav, requestsNav, studentsNav);
        });
        studentsNav.addActionListener(event -> {
            screenLayout.show(screens, "students");
            selectNavigation(studentsNav, overviewNav, requestsNav, studentsNav);
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

        students.setRowCount(0);

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
        }
    }

    private void refreshDashboard() throws IOException {
        Map<String, Prediction> predictions = jobs.getPredictions();
        long[] counts = new long[Job.Status.values().length];

        dashboardJobs.setRowCount(0);
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

            dashboardJobs.addRow(new Object[]{
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
            });
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

        StringBuilder balances = new StringBuilder("Balances: ");
        List<Student> allStudents = service.getStudents();
        for (int i = 0; i < allStudents.size(); i++) {
            Student student = allStudents.get(i);
            TimeAllowance allowance =
                    service.getAllowance(student.getUniversityId());
            if (i > 0) {
                balances.append(" | ");
            }
            balances.append(student.getUniversityId())
                    .append(": ")
                    .append(allowance.getAvailable())
                    .append(" available");
        }

        dashboardBalances.setText(balances.toString());
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
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableRowSorter<DefaultTableModel> sorter =
                new TableRowSorter<>(studentJobs);
        table.setRowSorter(sorter);

        JTextField jobSearch = new JTextField(22);
        JButton searchButton = new JButton("Search jobs");
        searchButton.addActionListener(event -> {
            String value = jobSearch.getText().trim();
            sorter.setRowFilter(value.isEmpty()
                    ? null
                    : RowFilter.regexFilter(
                            "(?i)" + java.util.regex.Pattern.quote(value)
                    ));
        });

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchBar.add(new JLabel("Search own jobs:"));
        searchBar.add(jobSearch);
        searchBar.add(searchButton);
        studentCancelButton = button("Cancel selected job", () -> {
            cancelSelectedJob(table);
            refreshStudent();
        });
        studentCancelButton.setEnabled(false);
        searchBar.add(studentCancelButton);

        studentJobDetails = new JTextArea(5, 40);
        studentJobDetails.setEditable(false);
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

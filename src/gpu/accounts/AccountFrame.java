package gpu.accounts;

import gpu.scheduling.GPU;
import gpu.scheduling.Job;
import gpu.scheduling.JobService;
import gpu.scheduling.Prediction;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

// Swing presentation for account management and the GPU simulation.
@SuppressWarnings("serial")
public final class AccountFrame extends JFrame {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color PAGE = new Color(248, 250, 252);
    private static final Color BLUE = new Color(35, 112, 183);
    private static final Color TEXT = new Color(35, 53, 73);
    private static final Color MUTED = new Color(101, 123, 147);
    private static final Color LINE = new Color(193, 205, 218);
    private static final Color ORANGE = new Color(219, 94, 0);
    private static final Color GREEN = new Color(0, 130, 102);

    private final AccountService service;
    private final JobService jobs;
    private final JPanel root = new JPanel(new BorderLayout(12, 12));

    private JLabel clockLabel;
    private JButton advanceButton;
    private JButton initializeClockButton;

    private DefaultTableModel requestModel;
    private DefaultTableModel balanceModel;
    private DefaultTableModel waitingModel;
    private DefaultTableModel completedModel;
    private DefaultTableModel adminJobsModel;
    private DefaultTableModel studentJobsModel;
    private DefaultTableModel selectedStudentJobsModel;

    private JTable requestTable;
    private JTable balanceTable;
    private JTable waitingTable;
    private JTable completedTable;
    private JTable adminJobsTable;
    private JTable studentJobsTable;
    private JTable selectedStudentJobsTable;

    private JComboBox<String> requestStatus;
    private JComboBox<String> adminJobStatus;
    private JComboBox<String> studentJobStatus;
    private JTextField adminJobSearch;
    private JTextField studentJobSearch;
    private JTextField studentIdSearch;
    private JTextField grantMinutes;
    private JTextField grantReason;
    private JComboBox<String> approvalAllowance;
    private JTextArea selectedStudentInfo;
    private JTextArea studentJobInfo;
    private JTextArea selectedStudentJobInfo;

    private JLabel studentClock;
    private JLabel[] studentBalanceValues;
    private JLabel[] adminBalanceValues;
    private JLabel[] summaryValues;
    private JPanel gpuCards;
    private JLabel requestCount;
    private JTabbedPane adminTabs;
    private JButton requestApprove;
    private JButton requestReject;
    private JButton studentCancelButton;
    private JButton studentSubmitButton;
    private JButton grantButton;
    private JButton adminJobCancelButton;
    private JButton selectedStudentCancelButton;
    private String selectedStudentId;

    private interface Action {
        void run() throws Exception;
    }

    public AccountFrame(AccountService service, JobService jobs) {
        super("GPU Utilization Management");
        this.service = service;
        this.jobs = jobs;

        UIManager.put("Panel.background", PAGE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("PasswordField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 14));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(root);
        root.setBackground(PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        setMinimumSize(new Dimension(900, 600));
        setSize(1160, 760);
        setLocationRelativeTo(null);
        showLogin();
    }

    private JButton button(String text, Action action, Color color) {
        JButton result = new JButton(text);
        result.setFont(new Font("Segoe UI", Font.BOLD, 13));
        result.setForeground(color == null ? TEXT : Color.WHITE);
        result.setBackground(color == null ? Color.WHITE : color);
        result.setFocusPainted(true);
        result.setOpaque(true);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        color == null ? LINE : color.darker()
                ),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)
        ));
        result.addActionListener(event -> {
            try {
                action.run();
            } catch (Exception exception) {
                showError(exception);
            }
        });
        return result;
    }

    private JButton primary(String text, Action action) {
        return button(text, action, BLUE);
    }

    private JButton danger(String text, Action action) {
        return button(text, action, ORANGE);
    }

    private void showError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        String title = exception instanceof IOException
                ? "File error" : "Unable to complete action";
        JOptionPane.showMessageDialog(
                this, message, title, JOptionPane.ERROR_MESSAGE
        );
    }

    private void redraw() {
        root.revalidate();
        root.repaint();
    }

    private static JPanel card(LayoutManager layout) {
        JPanel result = new JPanel(layout);
        result.setBackground(Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        return result;
    }

    private static JLabel label(String text) {
        JLabel result = new JLabel(text);
        result.setForeground(TEXT);
        result.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return result;
    }

    private static JLabel heading(String text) {
        JLabel result = new JLabel(text);
        result.setForeground(TEXT);
        result.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return result;
    }

    private static JTextField input(int columns) {
        JTextField result = new JTextField(columns);
        result.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        result.setForeground(TEXT);
        result.setBackground(Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        result.setPreferredSize(new Dimension(200, 32));
        return result;
    }

    private static JPasswordField passwordInput() {
        JPasswordField result = new JPasswordField();
        result.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        result.setPreferredSize(new Dimension(200, 32));
        return result;
    }

    private static JPanel titledField(String title, JComponent field) {
        JPanel result = new JPanel(new BorderLayout(0, 4));
        result.setOpaque(false);
        result.add(label(title), BorderLayout.NORTH);
        result.add(field, BorderLayout.CENTER);
        return result;
    }

    private void setMessage(JLabel target, String text, boolean success) {
        target.setText(text == null || text.isEmpty() ? " " : text);
        target.setOpaque(!text.isEmpty());
        target.setBackground(success
                ? new Color(224, 245, 239)
                : new Color(255, 240, 228));
        target.setForeground(success ? GREEN : ORANGE);
        target.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
    }

    private void showLogin() {
        root.removeAll();
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Color.WHITE);

        JPanel login = card(new BorderLayout(0, 12));
        login.setPreferredSize(new Dimension(590, 405));
        login.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(24, 34, 20, 34)
        ));

        JLabel chip = new JLabel("▦", SwingConstants.CENTER);
        chip.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 34));
        chip.setForeground(BLUE);
        JLabel title = heading("GPU Utilization Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel subtitle = label("4 × NVIDIA H100 · simulated");
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel brand = new JPanel(new GridLayout(3, 1, 0, 0));
        brand.setOpaque(false);
        brand.add(chip);
        brand.add(title);
        brand.add(subtitle);
        login.add(brand, BorderLayout.NORTH);

        JTextField username = input(28);
        JPasswordField password = passwordInput();
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 10));
        fields.setOpaque(false);
        fields.add(titledField("Username (university ID)", username));
        fields.add(titledField("Password", password));

        JLabel message = label(" ");
        message.setPreferredSize(new Dimension(440, 34));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JButton signIn = primary("Sign in", () -> {
            char[] chars = password.getPassword();
            try {
                service.login(username.getText(), new String(chars));
                setMessage(message, "", false);
                if (service.getCurrentUser().getRole() == Role.ADMINISTRATOR) {
                    showAdmin();
                } else {
                    showStudent();
                }
            } catch (IllegalArgumentException exception) {
                setMessage(message, exception.getMessage(), false);
                password.requestFocusInWindow();
            } finally {
                java.util.Arrays.fill(chars, '\0');
                password.setText("");
            }
        });
        signIn.setAlignmentX(Component.CENTER_ALIGNMENT);
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JButton register = new JButton("New student? Request an account");
        register.setForeground(BLUE);
        register.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        register.setBorderPainted(false);
        register.setContentAreaFilled(false);
        register.setAlignmentX(Component.CENTER_ALIGNMENT);
        register.addActionListener(event -> showRegistration());
        actions.add(signIn);
        actions.add(Box.createVerticalStrut(6));
        actions.add(register);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(fields, BorderLayout.NORTH);
        JPanel lower = new JPanel(new BorderLayout(0, 5));
        lower.setOpaque(false);
        lower.add(message, BorderLayout.NORTH);
        lower.add(actions, BorderLayout.CENTER);
        center.add(lower, BorderLayout.CENTER);
        login.add(center, BorderLayout.CENTER);
        page.add(login);
        root.add(page, BorderLayout.CENTER);
        getRootPane().setDefaultButton(signIn);
        SwingUtilities.invokeLater(() -> username.requestFocusInWindow());
        redraw();
    }

    private void showRegistration() {
        root.removeAll();
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Color.WHITE);
        JPanel form = card(new BorderLayout(0, 12));
        form.setPreferredSize(new Dimension(650, 465));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(18, 20, 16, 20)
        ));

        JLabel title = heading("Request a student account");
        JLabel subtitle = label(
                "An administrator must approve your request before you can sign in."
        );
        JPanel intro = new JPanel(new GridLayout(2, 1, 0, 4));
        intro.setOpaque(false);
        intro.add(title);
        intro.add(subtitle);
        form.add(intro, BorderLayout.NORTH);

        JTextField id = input(18);
        JTextField name = input(18);
        JTextField email = input(18);
        JTextField phone = input(18);
        JPasswordField password = passwordInput();
        JPanel fields = new JPanel(new GridLayout(3, 2, 12, 9));
        fields.setOpaque(false);
        fields.add(titledField("University ID", id));
        fields.add(titledField("Full name", name));
        fields.add(titledField("Email", email));
        fields.add(titledField("Phone number", phone));
        fields.add(titledField("Password", password));
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        fields.add(empty);

        JLabel message = label(" ");
        message.setPreferredSize(new Dimension(550, 34));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton send = primary("Submit request", () -> {
            char[] chars = password.getPassword();
            for (JTextField field : new JTextField[]{id, name, email, phone}) {
                field.setBorder(normalInputBorder());
            }
            try {
                service.register(
                        id.getText(), name.getText(), email.getText(),
                        phone.getText(), new String(chars)
                );
                setMessage(message, "✓ Request sent. Status: Pending.", true);
                email.setBorder(normalInputBorder());
            } catch (IllegalArgumentException exception) {
                setMessage(message, exception.getMessage(), false);
                if (exception.getMessage().toLowerCase().contains("email")) {
                    email.setBorder(errorInputBorder());
                } else if (exception.getMessage().toLowerCase().contains("phone")) {
                    phone.setBorder(errorInputBorder());
                } else if (exception.getMessage().toLowerCase().contains("university id")) {
                    id.setBorder(errorInputBorder());
                }
            } finally {
                java.util.Arrays.fill(chars, '\0');
                password.setText("");
            }
        });
        JButton back = button("Back to login", () -> showLogin(), null);
        actions.add(send);
        actions.add(back);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(fields, BorderLayout.NORTH);
        JPanel lower = new JPanel(new BorderLayout(0, 6));
        lower.setOpaque(false);
        lower.add(actions, BorderLayout.NORTH);
        lower.add(message, BorderLayout.CENTER);
        center.add(lower, BorderLayout.CENTER);
        form.add(center, BorderLayout.CENTER);
        page.add(form);
        root.add(page, BorderLayout.CENTER);
        getRootPane().setDefaultButton(send);
        redraw();
    }

    private static Border normalInputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        );
    }

    private static Border errorInputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ORANGE, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        );
    }

    private boolean showClockSetup() throws IOException {
        JSpinner date = new JSpinner(new SpinnerDateModel());
        date.setEditor(new JSpinner.DateEditor(date, "dd/MM/yyyy"));
        JSpinner time = new JSpinner(new SpinnerDateModel());
        time.setEditor(new JSpinner.DateEditor(time, "HH:mm"));
        JLabel note = label(
                "This is done once. Afterwards, time moves only with +1 minute."
        );
        JPanel setup = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        setup.add(heading("Set the starting date and time"), c);
        c.gridy++;
        setup.add(note, c);
        c.gridwidth = 1;
        c.gridy++;
        c.gridx = 0;
        setup.add(label("Date"), c);
        c.gridx = 1;
        setup.add(label("Time (24-hour)"), c);
        c.gridy++;
        c.gridx = 0;
        setup.add(date, c);
        c.gridx = 1;
        setup.add(time, c);
        Object[] options = {"Start clock", "Cancel"};
        int answer = JOptionPane.showOptionDialog(
                this, setup, "Start the simulation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]
        );
        if (answer != 0) {
            return false;
        }
        try {
            date.commitEdit();
            time.commitEdit();
        } catch (java.text.ParseException exception) {
            throw new IllegalArgumentException(
                    "Enter a valid date and time.", exception
            );
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = ((java.util.Date) date.getValue())
                .toInstant().atZone(zone).toLocalDate();
        LocalTime hour = ((java.util.Date) time.getValue())
                .toInstant().atZone(zone).toLocalTime();
        jobs.initializeClock(LocalDateTime.of(day, hour));
        return true;
    }

    private void showAdmin() throws IOException {
        if (!jobs.isClockInitialized()) {
            showClockSetup();
        }

        root.removeAll();
        JPanel shell = new JPanel(new BorderLayout(0, 8));
        shell.setBackground(PAGE);
        shell.add(adminHeader(), BorderLayout.NORTH);
        adminTabs = new JTabbedPane();
        adminTabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        adminTabs.setBackground(Color.WHITE);
        adminTabs.addTab("Dashboard", adminDashboard());
        adminTabs.addTab("Requests", adminRequests());
        adminTabs.addTab("Students", adminStudents());
        adminTabs.addTab("All jobs", adminAllJobs());
        adminTabs.addChangeListener(event -> refreshAdminDataSafely());
        shell.add(adminTabs, BorderLayout.CENTER);
        root.add(shell, BorderLayout.CENTER);
        refreshAdminData();
        redraw();
    }

    private JPanel adminHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(PAGE);
        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 1));
        titleBlock.setOpaque(false);
        titleBlock.add(heading("GPU Utilization Dashboard"));
        titleBlock.add(label("Administrator"));
        header.add(titleBlock, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        clockLabel = label("Simulated time: " + formattedTime(jobs.getNow()));
        clockLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        controls.add(clockLabel);
        initializeClockButton = primary("Set clock", () -> {
            if (showClockSetup()) {
                refreshAdminData();
            }
        });
        controls.add(initializeClockButton);
        advanceButton = primary("+1 minute", () -> {
            jobs.advanceMinute();
            refreshAdminData();
        });
        advanceButton.setEnabled(jobs.isClockInitialized());
        controls.add(advanceButton);
        controls.add(button("Log out", () -> {
            service.logout();
            showLogin();
        }, null));
        header.add(controls, BorderLayout.EAST);
        updateClockControls();
        return header;
    }

    private void updateClockControls() {
        if (clockLabel == null) {
            return;
        }
        boolean ready = jobs.isClockInitialized();
        clockLabel.setText("Simulated time: " + formattedTime(jobs.getNow()));
        advanceButton.setEnabled(ready);
        initializeClockButton.setVisible(!ready);
    }

    private JPanel adminDashboard() throws IOException {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBackground(PAGE);

        String[] metricTitles = {
                "Running", "Queued", "Completed", "Cancelled",
                "Busy GPUs", "Utilization"
        };
        Color[] metricColors = {
                new Color(225, 238, 250), new Color(252, 240, 218),
                new Color(222, 243, 235), new Color(235, 239, 244),
                new Color(236, 238, 250), new Color(230, 241, 252)
        };
        JPanel metrics = new JPanel(new GridLayout(1, 6, 8, 0));
        metrics.setBackground(PAGE);
        summaryValues = new JLabel[6];
        for (int i = 0; i < metricTitles.length; i++) {
            JPanel panel = metricCard(metricTitles[i], "0", metricColors[i]);
            summaryValues[i] = (JLabel) panel.getClientProperty("value");
            metrics.add(panel);
        }
        page.add(metrics);
        page.add(Box.createVerticalStrut(8));

        gpuCards = new JPanel(new GridLayout(1, 4, 8, 0));
        gpuCards.setBackground(PAGE);
        page.add(gpuCards);
        page.add(Box.createVerticalStrut(8));

        waitingModel = tableModel(
                "Job", "Student", "Application", "Dur.", "Pos.",
                "Predicted start", "Wait"
        );
        balanceModel = tableModel(
                "University ID", "Allocated", "Used", "Reserved", "Available"
        );
        waitingTable = styledTable(waitingModel, false);
        balanceTable = styledTable(balanceModel, false);

        JPanel waitingPanel = dataPanel(
                "Waiting jobs", new JScrollPane(waitingTable)
        );
        JPanel balancesPanel = dataPanel(
                "Student allowances", new JScrollPane(balanceTable)
        );
        JPanel queuesAndBalances = new JPanel(new GridLayout(1, 2, 8, 0));
        queuesAndBalances.setBackground(PAGE);
        queuesAndBalances.add(waitingPanel);
        queuesAndBalances.add(balancesPanel);
        page.add(queuesAndBalances);
        page.add(Box.createVerticalStrut(8));

        completedModel = tableModel(
                "Job", "Student", "Application", "GPU", "Submitted",
                "Started", "Finished", "Runtime / queue wait", "Charged"
        );
        completedTable = styledTable(completedModel, true);
        page.add(dataPanel(
                "Completed jobs", new JScrollPane(completedTable)
        ));
        return scrollPage(page);
    }

    private JPanel adminRequests() throws IOException {
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setBackground(PAGE);
        requestModel = tableModel(
                "University ID", "Name", "Email", "Phone", "Password", "Status"
        );
        requestTable = styledTable(requestModel, false);
        requestStatus = new JComboBox<>(new String[]{
                "All statuses", "Pending", "Approved", "Rejected"
        });
        requestStatus.setSelectedItem("Pending");
        requestStatus.addActionListener(event -> filterRequests());
        requestCount = heading("Registration requests");
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(requestCount, BorderLayout.WEST);
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filter.setOpaque(false);
        filter.add(label("Show:"));
        filter.add(requestStatus);
        top.add(filter, BorderLayout.EAST);
        page.add(top, BorderLayout.NORTH);
        page.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        JPanel decision = card(new BorderLayout(8, 8));
        JLabel selectedLabel = heading("Select a pending request");
        decision.add(selectedLabel, BorderLayout.NORTH);
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        settings.setOpaque(false);
        settings.add(label("Initial GPU minutes"));
        approvalAllowance = new JComboBox<>(new String[]{
                "0", "25", "50", "75", "100", "150", "200", "300", "500"
        });
        approvalAllowance.setEditable(true);
        approvalAllowance.setSelectedItem("100");
        settings.add(approvalAllowance);
        requestApprove = primary("Approve", () -> approveRequest());
        requestReject = danger("Reject", () -> rejectRequest());
        requestApprove.setEnabled(false);
        requestReject.setEnabled(false);
        settings.add(requestApprove);
        settings.add(requestReject);
        decision.add(settings, BorderLayout.CENTER);
        page.add(decision, BorderLayout.SOUTH);

        requestTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                boolean pending = selectedRequestIsPending();
                requestApprove.setEnabled(
                        pending && jobs.isClockInitialized()
                );
                requestReject.setEnabled(pending);
                String id = selectedRequestId();
                selectedLabel.setText(id == null
                        ? "Select a pending request"
                        : "Selected request: " + id);
            }
        });
        return page;
    }

    private JPanel adminStudents() throws IOException {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBackground(PAGE);
        studentIdSearch = input(20);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(label("University ID"));
        searchPanel.add(studentIdSearch);
        searchPanel.add(primary("Search", () -> loadSelectedStudent(
                studentIdSearch.getText().trim()
        )));
        studentIdSearch.addActionListener(
                event -> loadSelectedStudentSafely()
        );
        page.add(searchPanel, BorderLayout.NORTH);

        JPanel details = new JPanel();
        details.setBackground(PAGE);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        adminBalanceValues = new JLabel[4];
        String[] labels = {"Allocated", "Used", "Reserved", "Available"};
        Color[] colors = {
                new Color(225, 238, 250), new Color(236, 230, 250),
                new Color(252, 240, 218), new Color(222, 243, 235)
        };
        JPanel metrics = new JPanel(new GridLayout(1, 4, 8, 0));
        metrics.setBackground(PAGE);
        for (int i = 0; i < labels.length; i++) {
            JPanel item = metricCard(labels[i], "-", colors[i]);
            adminBalanceValues[i] = (JLabel) item.getClientProperty("value");
            metrics.add(item);
        }
        details.add(metrics);
        details.add(Box.createVerticalStrut(8));

        JPanel grant = card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        grant.add(heading("Grant extra time"), c);
        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0;
        grantMinutes = input(8);
        grantMinutes.setText("20");
        grant.add(titledField("Minutes", grantMinutes), c);
        c.gridx = 1;
        c.weightx = 1;
        grantReason = input(32);
        grant.add(titledField("Reason (required)", grantReason), c);
        c.gridx = 2;
        c.weightx = 0;
        grantButton = primary("Grant", () -> grantTime());
        grant.add(grantButton, c);
        c.gridx = 3;
        selectedStudentCancelButton = danger(
                "Cancel job...", () -> cancelSelected(selectedStudentJobsTable)
        );
        selectedStudentCancelButton.setEnabled(false);
        grant.add(selectedStudentCancelButton, c);
        details.add(grant);
        details.add(Box.createVerticalStrut(8));

        selectedStudentInfo = new JTextArea(3, 40);
        selectedStudentInfo.setEditable(false);
        selectedStudentInfo.setLineWrap(true);
        selectedStudentInfo.setWrapStyleWord(true);
        details.add(dataPanel("Student details and allowance history",
                new JScrollPane(selectedStudentInfo)));
        selectedStudentJobsModel = tableModel(
                "Job", "Title", "Status", "GPU", "Start", "Charged"
        );
        selectedStudentJobsTable = styledTable(selectedStudentJobsModel, false);
        selectedStudentJobInfo = new JTextArea(3, 40);
        selectedStudentJobInfo.setEditable(false);
        selectedStudentJobInfo.setLineWrap(true);
        selectedStudentJobInfo.setWrapStyleWord(true);
        selectedStudentJobsTable.getSelectionModel()
                .addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()) {
                        updateSelectedStudentJobInfo();
                        selectedStudentCancelButton.setEnabled(
                                selectedJobIsActive(selectedStudentJobsTable)
                        );
                    }
                });
        JPanel jobsPanel = new JPanel(new BorderLayout(0, 5));
        jobsPanel.setBackground(Color.WHITE);
        jobsPanel.add(heading("Student jobs"), BorderLayout.NORTH);
        jobsPanel.add(new JScrollPane(selectedStudentJobsTable),
                BorderLayout.CENTER);
        jobsPanel.add(new JScrollPane(selectedStudentJobInfo),
                BorderLayout.SOUTH);
        details.add(jobsPanel);
        page.add(new JScrollPane(details), BorderLayout.CENTER);
        setSelectedStudentState(false);
        return page;
    }

    private JPanel adminAllJobs() throws IOException {
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setBackground(PAGE);
        adminJobsModel = tableModel(
                "Job", "Student", "Title", "Application", "Size", "Status",
                "GPU", "Submitted", "Predicted start", "Predicted finish",
                "Wait", "Actual start", "Actual finish", "Runtime",
                "Charged", "Result", "Cancelled at", "Reason"
        );
        adminJobsTable = styledTable(adminJobsModel, true);
        adminJobSearch = input(20);
        adminJobStatus = statusFilter();
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(label("Search ID, student, or application:"));
        filters.add(adminJobSearch);
        filters.add(adminJobStatus);
        adminJobCancelButton = danger("Cancel selected job", () ->
                cancelSelected(adminJobsTable));
        adminJobCancelButton.setEnabled(false);
        filters.add(adminJobCancelButton);
        adminJobSearch.addActionListener(event -> filterAdminJobs());
        adminJobStatus.addActionListener(event -> filterAdminJobs());
        page.add(filters, BorderLayout.NORTH);
        page.add(new JScrollPane(adminJobsTable), BorderLayout.CENTER);
        adminJobsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                adminJobCancelButton.setEnabled(
                        selectedJobIsActive(adminJobsTable)
                );
            }
        });
        return page;
    }

    private void refreshAdminDataSafely() {
        if (requestModel == null) {
            return;
        }
        try {
            refreshAdminData();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void refreshAdminData() throws IOException {
        if (clockLabel != null) {
            updateClockControls();
        }
        if (grantButton != null) {
            grantButton.setEnabled(jobs.isClockInitialized());
        }
        if (requestModel != null) {
            requestModel.setRowCount(0);
            for (AccessRequest request : service.getRequests()) {
                Student applicant = request.getApplicant();
                requestModel.addRow(new Object[]{
                        applicant.getUniversityId(),
                        applicant.getName(),
                        applicant.getEmail(),
                        applicant.getPhone(),
                        "••••••",
                        prettyRequestStatus(request.getStatus())
                });
            }
            filterRequests();
            int pending = 0;
            for (int row = 0; row < requestModel.getRowCount(); row++) {
                if ("Pending".equals(requestModel.getValueAt(row, 5))) {
                    pending++;
                }
            }
            requestCount.setText("Registration requests (" + pending + ")");
            if (adminTabs != null) {
                adminTabs.setTitleAt(1, "Requests (" + pending + ")");
            }
            requestApprove.setEnabled(selectedRequestIsPending()
                    && jobs.isClockInitialized());
            requestReject.setEnabled(selectedRequestIsPending());
        }

        Map<String, Prediction> predictions = jobs.getPredictions();
        List<Job> all = jobs.getJobs();
        List<GPU> gpus = jobs.getGpus();
        if (gpuCards != null) {
            updateGpuCards(gpus);
        }
        if (waitingModel != null) {
            waitingModel.setRowCount(0);
            for (Job job : all) {
                if (job.getStatus() != Job.Status.QUEUED) {
                    continue;
                }
                Prediction prediction = predictions.get(job.getId());
                waitingModel.addRow(new Object[]{
                        job.getId(), job.getStudentId(), job.getApplication(),
                        job.getDuration(), prediction == null ? "-" : prediction.queuePosition(),
                        prediction == null ? "-" : recordTime(prediction.start()),
                        prediction == null ? "-" : prediction.remainingWait() + " min"
                });
            }
            balanceModel.setRowCount(0);
            for (Student student : service.getStudents()) {
                TimeAllowance allowance =
                        service.getAllowance(student.getUniversityId());
                balanceModel.addRow(new Object[]{
                        student.getUniversityId(), allowance.getAllocated(),
                        allowance.getUsed(), allowance.getReserved(),
                        allowance.getAvailable()
                });
            }
            completedModel.setRowCount(0);
            for (Job job : all) {
                if (job.getStatus() == Job.Status.COMPLETED) {
                    completedModel.addRow(completedRow(job));
                }
            }
            updateSummary(all, gpus);
        }
        if (adminJobsModel != null) {
            adminJobsModel.setRowCount(0);
            for (Job job : all) {
                adminJobsModel.addRow(adminJobRow(job, predictions.get(job.getId())));
            }
            filterAdminJobs();
            adminJobCancelButton.setEnabled(
                    selectedJobIsActive(adminJobsTable)
            );
        }
        if (selectedStudentId != null) {
            loadSelectedStudent(selectedStudentId);
        }
    }

    private void updateGpuCards(List<GPU> gpus) {
        gpuCards.removeAll();
        for (GPU gpu : gpus) {
            JPanel panel = card(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 2, 2, 2);
            c.gridx = 0;
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            JLabel title = heading("GPU" + gpu.id());
            panel.add(title, c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            JLabel availability = label(gpu.isAvailable() ? "Available" : "Busy");
            availability.setForeground(gpu.isAvailable() ? GREEN : BLUE);
            panel.add(availability, c);
            c.gridy++;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            panel.add(label("Current job"), c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            panel.add(label(gpu.jobId() == null ? "-" : gpu.jobId()), c);
            c.gridy++;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            panel.add(label("Remaining"), c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            panel.add(label(gpu.remainingMinutes() + " min"), c);
            c.gridy++;
            c.gridx = 0;
            panel.add(label("Finishes"), c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            panel.add(label(recordTime(gpu.expectedFinish())), c);
            gpuCards.add(panel);
        }
        gpuCards.revalidate();
        gpuCards.repaint();
    }

    private void updateSummary(List<Job> all, List<GPU> gpus) {
        int running = 0;
        int queued = 0;
        int completed = 0;
        int cancelled = 0;
        for (Job job : all) {
            switch (job.getStatus()) {
                case RUNNING: running++; break;
                case QUEUED: queued++; break;
                case COMPLETED: completed++; break;
                case CANCELLED: cancelled++; break;
                default: throw new IllegalStateException("Unknown job status.");
            }
        }
        long busy = gpus.stream().filter(gpu -> !gpu.isAvailable()).count();
        OptionalDouble utilization = jobs.getUtilization();
        summaryValues[0].setText(Integer.toString(running));
        summaryValues[1].setText(Integer.toString(queued));
        summaryValues[2].setText(Integer.toString(completed));
        summaryValues[3].setText(Integer.toString(cancelled));
        summaryValues[4].setText(busy + " / 4");
        summaryValues[5].setText(utilization.isPresent()
                ? String.format("%.1f%%", utilization.getAsDouble())
                : "N/A");
    }

    private JPanel metricCard(String title, String value, Color tint) {
        JPanel result = new JPanel(new BorderLayout(0, 4));
        result.setBackground(tint);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(198, 208, 220)),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        JLabel titleLabel = label(title);
        JLabel valueLabel = heading(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 21));
        result.add(titleLabel, BorderLayout.NORTH);
        result.add(valueLabel, BorderLayout.CENTER);
        result.putClientProperty("value", valueLabel);
        return result;
    }

    private JPanel dataPanel(String title, JComponent content) {
        JPanel result = card(new BorderLayout(0, 6));
        result.add(heading(title), BorderLayout.NORTH);
        result.add(content, BorderLayout.CENTER);
        result.setPreferredSize(new Dimension(400, 180));
        return result;
    }

    private static JPanel scrollPage(JComponent content) {
        JPanel result = new JPanel(new BorderLayout());
        result.setBackground(PAGE);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        result.add(scroll, BorderLayout.CENTER);
        return result;
    }

    private DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JTable styledTable(DefaultTableModel model, boolean wide) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setForeground(TEXT);
        table.setBackground(Color.WHITE);
        table.setRowHeight(27);
        table.setGridColor(new Color(222, 229, 236));
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(239, 243, 247));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable source, Object value, boolean selected,
                    boolean focus, int row, int column
            ) {
                Component cell = super.getTableCellRendererComponent(
                        source, value, selected, focus, row, column
                );
                String text = value == null ? "" : value.toString();
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
                setForeground(selected ? TEXT : new Color(54, 72, 92));
                setBackground(selected
                        ? new Color(220, 235, 248)
                        : Color.WHITE);
                if (!selected && isStatus(text)) {
                    setOpaque(true);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(new Font("Segoe UI", Font.BOLD, 11));
                    setForeground(statusColor(text));
                    setBackground(statusTint(text));
                } else {
                    setHorizontalAlignment(SwingConstants.LEADING);
                }
                return cell;
            }
        });
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(wide
                ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        return table;
    }

    private static boolean isStatus(String value) {
        return value.equals("Queued") || value.equals("Running")
                || value.equals("Completed") || value.equals("Cancelled")
                || value.equals("Pending") || value.equals("Approved")
                || value.equals("Rejected");
    }

    private static Color statusColor(String value) {
        switch (value) {
            case "Running":
            case "Approved": return GREEN;
            case "Completed": return BLUE;
            case "Cancelled":
            case "Rejected": return ORANGE;
            default: return new Color(171, 110, 0);
        }
    }

    private static Color statusTint(String value) {
        switch (value) {
            case "Running":
            case "Approved": return new Color(220, 243, 235);
            case "Completed": return new Color(221, 239, 251);
            case "Cancelled":
            case "Rejected": return new Color(255, 235, 221);
            default: return new Color(255, 245, 220);
        }
    }

    private void filterRequests() {
        if (requestTable == null) {
            return;
        }
        TableRowSorter<?> sorter = (TableRowSorter<?>) requestTable.getRowSorter();
        String choice = String.valueOf(requestStatus.getSelectedItem());
        sorter.setRowFilter("All statuses".equals(choice)
                ? null : RowFilter.regexFilter("^" + choice + "$", 5));
    }

    private void filterAdminJobs() {
        if (adminJobsTable == null) {
            return;
        }
        TableRowSorter<?> sorter =
                (TableRowSorter<?>) adminJobsTable.getRowSorter();
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String query = adminJobSearch.getText().trim();
        if (!query.isEmpty()) {
            String pattern = "(?i)" + java.util.regex.Pattern.quote(query);
            filters.add(RowFilter.orFilter(List.of(
                    RowFilter.regexFilter(pattern, 0),
                    RowFilter.regexFilter(pattern, 1),
                    RowFilter.regexFilter(pattern, 3)
            )));
        }
        String status = String.valueOf(adminJobStatus.getSelectedItem());
        if (!"All statuses".equals(status)) {
            filters.add(RowFilter.regexFilter(
                    "^" + status + "$", 5
            ));
        }
        sorter.setRowFilter(filters.isEmpty()
                ? null : RowFilter.andFilter(filters));
    }

    private JComboBox<String> statusFilter() {
        return new JComboBox<>(new String[]{
                "All statuses", "Queued", "Running", "Completed", "Cancelled"
        });
    }

    private void filterStudentJobs() {
        if (studentJobsTable == null) {
            return;
        }
        TableRowSorter<?> sorter =
                (TableRowSorter<?>) studentJobsTable.getRowSorter();
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String query = studentJobSearch.getText().trim();
        if ("Search by job ID or title...".equals(query)) {
            query = "";
        }
        if (!query.isEmpty()) {
            String pattern = "(?i)" + java.util.regex.Pattern.quote(query);
            filters.add(RowFilter.orFilter(List.of(
                    RowFilter.regexFilter(pattern, 0),
                    RowFilter.regexFilter(pattern, 1)
            )));
        }
        String status = String.valueOf(studentJobStatus.getSelectedItem());
        if (!"All statuses".equals(status)) {
            filters.add(RowFilter.regexFilter("^" + status + "$", 4));
        }
        sorter.setRowFilter(filters.isEmpty()
                ? null : RowFilter.andFilter(filters));
    }

    private boolean selectedRequestIsPending() {
        if (requestTable == null || requestTable.getSelectedRow() < 0) {
            return false;
        }
        int row = requestTable.convertRowIndexToModel(
                requestTable.getSelectedRow()
        );
        return "Pending".equals(requestModel.getValueAt(row, 5));
    }

    private String selectedRequestId() {
        if (requestTable == null || requestTable.getSelectedRow() < 0) {
            return null;
        }
        int row = requestTable.convertRowIndexToModel(
                requestTable.getSelectedRow()
        );
        return String.valueOf(requestModel.getValueAt(row, 0));
    }

    private void approveRequest() throws IOException {
        String id = selectedRequestId();
        if (id == null || !selectedRequestIsPending()) {
            throw new IllegalArgumentException("Select a pending request.");
        }
        Object raw = approvalAllowance.getEditor().getItem();
        int minutes;
        try {
            minutes = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Enter a whole number for the initial allowance."
            );
        }
        if (!jobs.isClockInitialized()) {
            throw new IllegalStateException(
                    "Set the simulation clock before approving accounts."
            );
        }
        service.approve(id, minutes, jobs.getNow());
        refreshAdminData();
    }

    private void rejectRequest() throws IOException {
        String id = selectedRequestId();
        if (id == null || !selectedRequestIsPending()) {
            throw new IllegalArgumentException("Select a pending request.");
        }
        service.reject(id);
        refreshAdminData();
    }

    private void loadSelectedStudent(String id) throws IOException {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a university ID.");
        }
        Student student = service.findStudent(id.trim());
        TimeAllowance allowance = service.getAllowance(id.trim());
        selectedStudentId = student.getUniversityId();
        studentIdSearch.setText(selectedStudentId);
        adminBalanceValues[0].setText(Integer.toString(allowance.getAllocated()));
        adminBalanceValues[1].setText(Integer.toString(allowance.getUsed()));
        adminBalanceValues[2].setText(Integer.toString(allowance.getReserved()));
        adminBalanceValues[3].setText(Integer.toString(allowance.getAvailable()));

        StringBuilder text = new StringBuilder();
        text.append(student.getName()).append(" (")
                .append(student.getUniversityId()).append(")\n")
                .append(student.getEmail()).append(" | ")
                .append(student.getPhone()).append("\n\nAllowance changes:\n");
        for (AllowanceChange change : service.getChanges(selectedStudentId)) {
            text.append(change).append('\n');
        }
        selectedStudentInfo.setText(text.toString());

        selectedStudentJobsModel.setRowCount(0);
        for (Job job : jobs.getJobs()) {
            if (!job.getStudentId().equals(selectedStudentId)) {
                continue;
            }
            selectedStudentJobsModel.addRow(new Object[]{
                    job.getId(), job.getTitle(), prettyStatus(job.getStatus()),
                    gpuText(job), recordTime(job.getStartTime()),
                    isSettled(job) ? job.getChargedMinutes() : "-"
            });
        }
        selectedStudentCancelButton.setEnabled(false);
    }

    private void loadSelectedStudentSafely() {
        try {
            loadSelectedStudent(studentIdSearch.getText().trim());
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void setSelectedStudentState(boolean selected) {
        for (JLabel value : adminBalanceValues) {
            value.setText("-");
        }
        selectedStudentInfo.setText(selected
                ? selectedStudentInfo.getText() : "Search by university ID.");
        selectedStudentJobsModel.setRowCount(0);
        selectedStudentJobInfo.setText("");
        selectedStudentCancelButton.setEnabled(false);
    }

    private void grantTime() throws IOException {
        if (selectedStudentId == null) {
            throw new IllegalArgumentException("Search for a student first.");
        }
        if (!jobs.isClockInitialized()) {
            throw new IllegalStateException(
                    "Set the simulation clock before granting GPU time."
            );
        }
        int minutes;
        try {
            minutes = Integer.parseInt(grantMinutes.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Enter a whole number of minutes."
            );
        }
        String reason = grantReason.getText().trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException(
                    "A reason for the allowance change is required."
            );
        }
        service.grantExtraTime(
                selectedStudentId, minutes, reason, jobs.getNow()
        );
        grantReason.setText("");
        refreshAdminData();
    }

    private void updateSelectedStudentJobInfo() {
        if (selectedStudentJobsTable == null
                || selectedStudentJobsTable.getSelectedRow() < 0) {
            selectedStudentJobInfo.setText("");
            return;
        }
        Job job = jobAt(selectedStudentJobsTable);
        selectedStudentJobInfo.setText(jobDetails(job));
    }

    private void showStudent() throws IOException {
        root.removeAll();
        JPanel shell = new JPanel(new BorderLayout(0, 8));
        shell.setBackground(PAGE);
        shell.add(studentHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.addTab("Home", studentHome());
        tabs.addTab("My jobs", studentMyJobs());
        shell.add(tabs, BorderLayout.CENTER);
        root.add(shell, BorderLayout.CENTER);
        refreshStudent();
        redraw();
    }

    private JPanel studentHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(PAGE);
        User user = service.getCurrentUser();
        JPanel identity = new JPanel(new GridLayout(2, 1, 0, 1));
        identity.setOpaque(false);
        identity.add(heading("Welcome, " + user.getName()));
        identity.add(label("Student · " + user.getUsername()));
        header.add(identity, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        studentClock = label("Simulated time: " + formattedTime(jobs.getNow()));
        controls.add(studentClock);
        controls.add(button("Log out", () -> {
            service.logout();
            showLogin();
        }, null));
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel studentHome() throws IOException {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBackground(PAGE);
        String[] names = {"Allocated", "Used", "Reserved", "Available"};
        Color[] colors = {
                new Color(225, 238, 250), new Color(236, 230, 250),
                new Color(252, 240, 218), new Color(222, 243, 235)
        };
        JPanel metrics = new JPanel(new GridLayout(1, 4, 8, 0));
        metrics.setBackground(PAGE);
        studentBalanceValues = new JLabel[4];
        for (int i = 0; i < names.length; i++) {
            JPanel item = metricCard(names[i], "0", colors[i]);
            studentBalanceValues[i] = (JLabel) item.getClientProperty("value");
            metrics.add(item);
        }
        page.add(metrics);
        page.add(Box.createVerticalStrut(9));
        page.add(jobSubmissionForm());
        return scrollPage(page);
    }

    private JPanel jobSubmissionForm() {
        JPanel form = card(new BorderLayout(8, 8));
        form.add(heading("Submit a new job"), BorderLayout.NORTH);

        JTextField title = input(22);
        JTextField application = input(22);
        JTextArea description = new JTextArea(3, 30);
        description.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        JComboBox<String> size = new JComboBox<>(new String[]{
                "Small (5 units)", "Medium (15 units)",
                "Large (30 units)", "Very large (60 units)"
        });
        JLabel duration = label("Duration: 5 minutes");
        int[] durations = {5, 15, 30, 60};
        size.addActionListener(event -> duration.setText(
                "Duration: " + durations[size.getSelectedIndex()] + " minutes"
        ));

        JPanel names = new JPanel(new GridLayout(1, 2, 10, 0));
        names.setOpaque(false);
        names.add(titledField("Job title", title));
        names.add(titledField("Application name", application));
        JPanel fields = new JPanel(new BorderLayout(0, 8));
        fields.setOpaque(false);
        fields.add(names, BorderLayout.NORTH);
        fields.add(titledField("Description", new JScrollPane(description)),
                BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.setOpaque(false);
        bottom.add(titledField("Task size", size));
        bottom.add(duration);
        studentSubmitButton = primary("Submit job", () -> submitStudentJob(
                title, application, description, size
        ));
        studentSubmitButton.setEnabled(jobs.isClockInitialized());
        studentSubmitButton.setToolTipText(jobs.isClockInitialized()
                ? null : "An administrator must set the simulation clock first.");
        bottom.add(studentSubmitButton);
        fields.add(bottom, BorderLayout.SOUTH);
        form.add(fields, BorderLayout.CENTER);
        return form;
    }

    private JPanel studentMyJobs() throws IOException {
        JPanel page = new JPanel(new BorderLayout(0, 7));
        page.setBackground(PAGE);
        studentJobsModel = tableModel(
                "Job", "Title", "Application", "Size", "Status",
                "Queue position", "GPU", "Submitted", "Start", "Finish", "Wait"
        );
        studentJobsTable = styledTable(studentJobsModel, true);
        studentJobSearch = input(24);
        studentJobSearch.setText("Search by job ID or title...");
        studentJobSearch.setForeground(MUTED);
        studentJobSearch.setToolTipText("Search by job ID or title");
        studentJobSearch.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                if ("Search by job ID or title...".equals(
                        studentJobSearch.getText())) {
                    studentJobSearch.setText("");
                    studentJobSearch.setForeground(TEXT);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                if (studentJobSearch.getText().trim().isEmpty()) {
                    studentJobSearch.setText("Search by job ID or title...");
                    studentJobSearch.setForeground(MUTED);
                }
            }
        });
        studentJobStatus = statusFilter();
        studentCancelButton = danger("Cancel selected job", () ->
                cancelSelected(studentJobsTable));
        studentCancelButton.setEnabled(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(studentJobSearch);
        filters.add(studentJobStatus);
        filters.add(studentCancelButton);
        studentJobSearch.addActionListener(event -> filterStudentJobs());
        studentJobStatus.addActionListener(event -> filterStudentJobs());
        page.add(filters, BorderLayout.NORTH);
        page.add(new JScrollPane(studentJobsTable), BorderLayout.CENTER);
        studentJobInfo = new JTextArea(4, 40);
        studentJobInfo.setEditable(false);
        studentJobInfo.setLineWrap(true);
        studentJobInfo.setWrapStyleWord(true);
        studentJobInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        page.add(new JScrollPane(studentJobInfo), BorderLayout.SOUTH);
        studentJobsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                studentCancelButton.setEnabled(
                        selectedJobIsActive(studentJobsTable)
                );
                updateStudentJobInfo();
            }
        });
        return page;
    }

    private void refreshStudent() throws IOException {
        if (studentBalanceValues == null) {
            return;
        }
        updateStudentClock();
        studentSubmitButton.setEnabled(jobs.isClockInitialized());
        studentSubmitButton.setToolTipText(jobs.isClockInitialized()
                ? null : "An administrator must set the simulation clock first.");
        String id = service.getCurrentUser().getUsername();
        TimeAllowance allowance = service.getAllowance(id);
        studentBalanceValues[0].setText(Integer.toString(allowance.getAllocated()));
        studentBalanceValues[1].setText(Integer.toString(allowance.getUsed()));
        studentBalanceValues[2].setText(Integer.toString(allowance.getReserved()));
        studentBalanceValues[3].setText(Integer.toString(allowance.getAvailable()));

        if (studentJobsModel == null) {
            return;
        }
        Map<String, Prediction> predictions = jobs.getPredictions();
        studentJobsModel.setRowCount(0);
        for (Job job : jobs.getJobs()) {
            Prediction prediction = predictions.get(job.getId());
            studentJobsModel.addRow(new Object[]{
                    job.getId(), job.getTitle(), job.getApplication(),
                    shortSize(job), prettyStatus(job.getStatus()),
                    prediction == null || job.getStatus() != Job.Status.QUEUED
                            ? "-" : prediction.queuePosition(),
                    gpuText(job), recordTime(job.getSubmissionTime()),
                    job.getStartTime() == null && prediction != null
                            ? "≈ " + formattedTime(prediction.start())
                            : recordTime(job.getStartTime()),
                    job.getFinishTime() == null && prediction != null
                            ? "≈ " + formattedTime(prediction.finish())
                            : recordTime(job.getFinishTime()),
                    prediction != null ? prediction.remainingWait() + " min"
                            : job.getStatus() == Job.Status.QUEUED ? "-" : "0 min"
            });
        }
        filterStudentJobs();
        studentCancelButton.setEnabled(
                selectedJobIsActive(studentJobsTable)
        );
    }

    private void updateStudentClock() {
        if (studentClock != null) {
            studentClock.setText("Simulated time: "
                    + formattedTime(jobs.getNow()));
        }
    }

    private void submitStudentJob(
            JTextField title,
            JTextField application,
            JTextArea description,
            JComboBox<String> size
    ) throws IOException {
        if (!jobs.isClockInitialized()) {
            throw new IllegalStateException(
                    "The administrator must set the simulation clock before jobs can be submitted."
            );
        }
        String studentId = service.getCurrentUser().getUsername();
        TimeAllowance allowance = service.getAllowance(studentId);
        int[] durations = {5, 15, 30, 60};
        int duration = durations[size.getSelectedIndex()];
        int available = allowance.getAvailable();
        if (duration > available) {
            JOptionPane.showMessageDialog(
                    this,
                    "This job needs " + duration + " min, but you have only "
                            + available + " min available.\nChoose a smaller size or ask the administrator for more time.",
                    "Not enough GPU time",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Submit " + title.getText().trim() + " as a "
                        + sizeName(duration) + " job?\n\nDuration\t"
                        + duration + " min\nAvailable after submitting\t"
                        + (available - duration) + " min",
                "Confirm job",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        jobs.submitJob(
                title.getText(), application.getText(),
                description.getText(), duration
        );
        refreshStudent();
    }

    private void cancelSelected(JTable table) throws IOException {
        Job job = jobAt(table);
        if (job.getStatus() != Job.Status.QUEUED
                && job.getStatus() != Job.Status.RUNNING) {
            throw new IllegalStateException(
                    "Only queued or running jobs can be cancelled."
            );
        }
        int charge = job.getStatus() == Job.Status.RUNNING
                ? (int) ChronoUnit.MINUTES.between(
                        job.getStartTime(), jobs.getNow()
                )
                : 0;
        int release = job.getDuration() - charge;
        JTextField reason = input(36);
        JPanel dialog = new JPanel(new GridLayout(0, 1, 0, 5));
        dialog.add(label(job.getId() + " is " + prettyStatus(job.getStatus())
                + (job.getGpuId() == null ? "" : " on GPU" + job.getGpuId())
                + "."));
        dialog.add(label(charge + " min will be charged; "
                + release + " reserved min will be released."));
        dialog.add(titledField("Reason (required)", reason));
        Object[] options = {"Cancel job", "Keep job"};
        int answer = JOptionPane.showOptionDialog(
                this, dialog, "Cancel job " + job.getId(),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]
        );
        if (answer != 0) {
            return;
        }
        if (reason.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("A cancellation reason is required.");
        }
        jobs.cancelJob(job.getId(), reason.getText().trim());
        if (service.getCurrentUser().getRole() == Role.ADMINISTRATOR) {
            refreshAdminData();
        } else {
            refreshStudent();
        }
    }

    private Job jobAt(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            throw new IllegalArgumentException("Select a job first.");
        }
        int modelRow = table.convertRowIndexToModel(selected);
        String id = String.valueOf(table.getModel().getValueAt(modelRow, 0));
        return jobs.getJobs().stream()
                .filter(job -> job.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Job not found."));
    }

    private boolean selectedJobIsActive(JTable table) {
        if (table == null || table.getSelectedRow() < 0) {
            return false;
        }
        try {
            Job.Status status = jobAt(table).getStatus();
            return status == Job.Status.QUEUED || status == Job.Status.RUNNING;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void updateStudentJobInfo() {
        if (studentJobsTable == null || studentJobsTable.getSelectedRow() < 0) {
            studentJobInfo.setText("");
            return;
        }
        studentJobInfo.setText(jobDetails(jobAt(studentJobsTable)));
    }

    private String jobDetails(Job job) {
        Map<String, Prediction> predictions = jobs.getPredictions();
        Prediction prediction = predictions.get(job.getId());
        StringBuilder text = new StringBuilder()
                .append(job.getTitle()).append(" · ")
                .append(job.getApplication()).append(" · ")
                .append(job.getTaskSize()).append(" · ")
                .append(job.getWorkloadUnits()).append(" workload units / ")
                .append(job.getDuration()).append(" min\n")
                .append(job.getDescription()).append("\n")
                .append("Status: ").append(prettyStatus(job.getStatus()))
                .append(" | GPU: ").append(gpuText(job))
                .append(" | Submitted: ").append(recordTime(job.getSubmissionTime()));
        if (prediction != null && job.getStatus() == Job.Status.QUEUED) {
            text.append("\nPredicted start: ")
                    .append(recordTime(prediction.start()))
                    .append(" | finish: ").append(recordTime(prediction.finish()))
                    .append(" | wait: ").append(prediction.remainingWait())
                    .append(" min");
        }
        text.append("\nActual start: ").append(recordTime(job.getStartTime()))
                .append(" | actual finish: ").append(recordTime(job.getFinishTime()))
                .append(" | runtime charged: ")
                .append(isSettled(job) ? job.getChargedMinutes() + " min" : "-")
                .append("\nResult: ")
                .append(job.getResult() == null ? "-" : job.getResult());
        if (job.getCancellationTime() != null) {
            text.append("\nCancelled: ").append(recordTime(job.getCancellationTime()))
                    .append(" by ").append(job.getCancelledBy())
                    .append(" | reason: ").append(job.getCancellationReason());
        }
        return text.toString();
    }

    private Object[] completedRow(Job job) {
        long queueWait = ChronoUnit.MINUTES.between(
                job.getSubmissionTime(), job.getStartTime()
        );
        return new Object[]{
                job.getId(), job.getStudentId(), job.getApplication(),
                gpuText(job), recordTime(job.getSubmissionTime()),
                recordTime(job.getStartTime()), recordTime(job.getFinishTime()),
                job.getChargedMinutes() + " min / " + queueWait + " min",
                job.getChargedMinutes()
        };
    }

    private Object[] adminJobRow(Job job, Prediction prediction) {
        long runtime = job.getStartTime() == null
                ? 0
                : ChronoUnit.MINUTES.between(
                        job.getStartTime(),
                        job.getFinishTime() == null
                                ? jobs.getNow() : job.getFinishTime()
                );
        String wait = prediction == null ? "-"
                : prediction.remainingWait() + " min";
        return new Object[]{
                job.getId(), job.getStudentId(), job.getTitle(),
                job.getApplication(), shortSize(job), prettyStatus(job.getStatus()),
                gpuText(job), recordTime(job.getSubmissionTime()),
                prediction == null || job.getStatus() != Job.Status.QUEUED
                        ? "-" : recordTime(prediction.start()),
                prediction == null || job.getStatus() != Job.Status.QUEUED
                        ? "-" : recordTime(prediction.finish()),
                wait, recordTime(job.getStartTime()), recordTime(job.getFinishTime()),
                job.getStartTime() == null ? "-" : runtime,
                isSettled(job) ? job.getChargedMinutes() : "-",
                job.getResult() == null ? "-" : job.getResult(),
                recordTime(job.getCancellationTime()),
                job.getCancellationReason() == null ? "-" : job.getCancellationReason()
        };
    }

    private static String gpuText(Job job) {
        return job.getGpuId() == null ? "-" : "GPU" + job.getGpuId();
    }

    private static String prettyStatus(Job.Status status) {
        String value = status.toString().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String prettyRequestStatus(AccessRequest.Status status) {
        String value = status.toString().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String shortSize(Job job) {
        String name = job.getTaskSize();
        return name + " (" + job.getDuration() + ")";
    }

    private static String sizeName(int duration) {
        switch (duration) {
            case 5: return "Small";
            case 15: return "Medium";
            case 30: return "Large";
            case 60: return "Very large";
            default: throw new IllegalArgumentException("Unknown job size.");
        }
    }

    private static String formattedTime(LocalDateTime value) {
        return value == null ? "Not set" : TIME.format(value);
    }

    private static String recordTime(LocalDateTime value) {
        return value == null ? "-" : TIME.format(value);
    }

    private static boolean isSettled(Job job) {
        return job.getStatus() == Job.Status.COMPLETED
                || job.getStatus() == Job.Status.CANCELLED;
    }
}

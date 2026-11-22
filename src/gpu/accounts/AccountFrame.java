package gpu.accounts;

import java.awt.*;
import java.io.IOException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// Builds the application window and its screens.
// AccountService handles validation, permissions and saved data.
@SuppressWarnings("serial")
public final class AccountFrame extends JFrame {
    private final AccountService service;
    private final JobHistoryProvider jobs;

    // Main panel whose contents change when switching screens.
    private final JPanel body =
            new JPanel(new BorderLayout(12, 12));

    private DefaultTableModel requests;
    private DefaultTableModel students;
    private JTextField search;

    // Displays the selected student's information and history.
    private final JTextArea details = new JTextArea();

    public AccountFrame(AccountService service,
                        JobHistoryProvider jobs) {

        super("GPU Management - Accounts and Administration");

        this.service = service;
        this.jobs = jobs;

        // Close the application when the window is closed.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(body);

        body.setBorder(
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        );

        setSize(1050, 700);
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

        JLabel title = new JLabel(heading);
        title.setFont(
                title.getFont().deriveFont(Font.BOLD, 23f)
        );

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

        for (int i = 0; i < labels.length; i++) {
            panel.add(new JLabel(labels[i]));
            panel.add(fields[i]);
        }

        return panel;
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
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        // Allows sorting by clicking column headings.
        table.setAutoCreateRowSorter(true);

        return table;
    }

    // Reads the university ID from the selected request row.
    private String selectedId(JTable table) {
        int row = table.getSelectedRow();

        if (row < 0) {
            throw new IllegalArgumentException(
                    "Select a request first."
            );
        }

        return String.valueOf(table.getValueAt(row, 0));
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

    // Displays administrator functions in two tabs.
    private void showAdmin() throws IOException {
        reset("Administrator | Accounts and allowances");

        JTabbedPane tabs = new JTabbedPane();

        // First tab: review student registration requests.
        requests = model(
                "University ID", "Name", "Email", "Phone", "Status"
        );

        JTable requestTable = table(requests);

        JPanel requestPanel =
                new JPanel(new BorderLayout(10, 10));

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
                })
        );

        requestActions.add(button("Reject", () -> {
            service.reject(selectedId(requestTable));
            refreshAdmin();
        }));

        requestActions.add(
                button("Refresh", this::refreshAdmin)
        );

        requestPanel.add(requestActions, BorderLayout.SOUTH);
        tabs.addTab("Registration requests", requestPanel);

        // Second tab: search students and manage their allowances.
        students = model(
                "University ID", "Name", "Email", "Phone",
                "Allocated", "Used", "Reserved", "Available"
        );

        JTable studentTable = table(students);
        search = new JTextField(16);

        JPanel searchBar =
                new JPanel(new FlowLayout(FlowLayout.LEFT));

        searchBar.add(new JLabel("University ID:"));
        searchBar.add(search);

        searchBar.add(
                button("View student", this::showStudentDetails)
        );

        searchBar.add(button("Grant extra time", () -> {
            String id = search.getText().trim();
            int value = minutes("Additional minutes:", 30);
            String note = reason();

            service.grantExtraTime(id, value, note);

            refreshAdmin();
            showStudentDetails();
        }));

        searchBar.add(button("Set total allocation", () -> {
            String id = search.getText().trim();

            int value = minutes(
                    "New TOTAL allocation:",
                    service.getAllowance(id).getAllocated()
            );

            service.setAllocation(id, value, reason());

            refreshAdmin();
            showStudentDetails();
        }));

        // Selecting a student copies their university ID into the search field.
        studentTable.getSelectionModel()
                .addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()
                            && studentTable.getSelectedRow() >= 0) {

                        search.setText(
                                String.valueOf(
                                        studentTable.getValueAt(
                                                studentTable.getSelectedRow(),
                                                0
                                        )
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

        split.setResizeWeight(0.55);

        JPanel studentPanel = new JPanel(new BorderLayout());
        studentPanel.add(searchBar, BorderLayout.NORTH);
        studentPanel.add(split, BorderLayout.CENTER);

        tabs.addTab(
                "Students, balances and history",
                studentPanel
        );

        body.add(tabs, BorderLayout.CENTER);

        body.add(
                button("Log out", () -> {
                    service.logout();
                    showLogin();
                }),
                BorderLayout.SOUTH
        );

        // Fill both tables with current account data.
        refreshAdmin();
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

        // Obtain this student's jobs from Person 2's connected module.
        for (String job : jobs.getJobsForStudent(id)) {
            text.append(job).append("\n");
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

        JTextArea info = new JTextArea(
                studentDetails(
                        service.getCurrentUser().getUsername()
                )
        );

        info.setEditable(false);
        info.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 15)
        );

        body.add(
                new JScrollPane(info),
                BorderLayout.CENTER
        );

        JPanel actions = new JPanel();

        actions.add(button("Refresh", this::showStudent));

        actions.add(button("Log out", () -> {
            service.logout();
            showLogin();
        }));

        body.add(actions, BorderLayout.SOUTH);

        redraw();
    }
}



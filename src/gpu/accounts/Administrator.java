package gpu.accounts;

// Administrator inherits the shared account information from User.
public final class Administrator extends User {
    private static final long serialVersionUID = 1L;

    Administrator() {
        // Creates the predefined administrator account.
        super("admin", "Administrator", new Credential("gpu2026"));
    }

    // Identifies this user as an administrator.
    @Override
    public Role getRole() {
        return Role.ADMINISTRATOR;
    }
}
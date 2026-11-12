package gpu.accounts;

import java.io.*;

// Parent class for student and administrator accounts.
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String name;
    private final Credential credential;

    protected User(String username, String name, Credential credential) {
        this.username = username;
        this.name = name;
        this.credential = credential;
    }

//Getter methods for name and  username.
    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public boolean matchesPassword(String password) {
        return credential.matches(password);
    }

    // Each subclass provides its own role.
    public abstract Role getRole();
}

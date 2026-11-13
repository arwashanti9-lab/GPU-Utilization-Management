package gpu.accounts;

import java.io.*;

// Abstract parent class containing information shared by all users.
// Serializable interface allows user objects to be saved to a file.
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    // Private fields to prevent other classes from changing them directly.
    private final String username;
    private final String name;
    private final Credential credential;
    
    // Sets the username, name and password information when creating a Student or Administrator.
    protected User(String username, String name, Credential credential) {
        this.username = username;
        this.name = name;
        this.credential = credential;
    }
    
    //Getter methods for name and username.
    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    // Checks whether the entered password matches this user's password.
    public boolean matchesPassword(String password) {
        return credential.matches(password);
    }

    // Student and Administrator each provide their own account role.
    public abstract Role getRole();
}
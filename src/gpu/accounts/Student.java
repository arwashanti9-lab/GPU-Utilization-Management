package gpu.accounts;

// Student inherits the username, name and password methods from User.
public final class Student extends User {
    private static final long serialVersionUID = 1L;

    // Additional information belonging to a student.
    private final String email;
    private final String phone;

    Student(String id, String name, String email,
            String phone, Credential credential) {

        // The university ID becomes the student's username.
        super(id, name, credential);

        this.email = email;
        this.phone = phone;
    }
    
    //Getter methods for ID,Email,and phone.
    public String getUniversityId() {
        return getUsername();
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    // Overrides the abstract method inherited from User.
    @Override
    public Role getRole() {
        return Role.STUDENT;
    }
}
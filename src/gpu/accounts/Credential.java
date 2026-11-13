package gpu.accounts;

import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

// Handles password storage and checking.
final class Credential implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Makes the stored password hashes different even when two users choose the same password.
    private final byte[] salt = new byte[16];
    
    // Stores the password's hash used to check login attempts.
    private final byte[] hash;

    Credential(String password) {
        new SecureRandom().nextBytes(salt);
        hash = derive(password);
    }
    
    // Converts the password into a hash for storage and login checks.
    private byte[] derive(String password) {
        char[] characters = password.toCharArray();

        PBEKeySpec specification =
                new PBEKeySpec(characters, salt, 65536, 256);

        try {
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Password verification is unavailable.", exception
            );

        } finally {
            // Clears the temporary password character arrays after use.
            specification.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }

    // Calculates the entered password's hash and compares it with the stored one.
    boolean matches(String password) {
        return password != null
                && MessageDigest.isEqual(hash, derive(password));
    }
}
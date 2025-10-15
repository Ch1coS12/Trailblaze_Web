package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Base64;
import java.security.MessageDigest;

public class HashUtil {

    public static String hashPassword(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        try {
            String hashOfInput = hashPassword(plainPassword);
            return hashOfInput.equals(hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}

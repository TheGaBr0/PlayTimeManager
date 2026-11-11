package me.thegabro.playtimemanager.Web;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Dashboard accounts, one password each. Passwords are never stored: only a salted
 * PBKDF2 hash, one line per account ({@code name:iterations:salt:hash}) in a plain file.
 */
public class WebUsers {

    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final int ITERATIONS = 120_000;
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final File file;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String[]> users = new TreeMap<>(); // lower-case name -> {display, iterations, salt, hash}
    private final String[] dummy; // verified against for unknown names, so timing does not reveal which exist

    public WebUsers(File file) {
        this.file = file;
        this.dummy = hash("dummy-password", newSalt(), ITERATIONS);
        load();
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]{1,32}");
    }

    public synchronized boolean exists(String name) {
        return name != null && users.containsKey(name.toLowerCase());
    }

    public synchronized List<String> names() {
        List<String> out = new ArrayList<>();
        users.values().forEach(u -> out.add(u[0]));
        return out;
    }

    /** Creates the account, or replaces its password. Returns false for a bad name or a too short password. */
    public synchronized boolean set(String name, String password) throws IOException {
        if (!validName(name) || password == null || password.length() < MIN_PASSWORD_LENGTH) return false;
        String[] h = hash(password, newSalt(), ITERATIONS);
        users.put(name.toLowerCase(), new String[]{name, h[0], h[1], h[2]});
        save();
        return true;
    }

    public synchronized boolean remove(String name) throws IOException {
        if (name == null || users.remove(name.toLowerCase()) == null) return false;
        save();
        return true;
    }

    /** Returns the account's display name when the password matches, null otherwise. */
    public String verify(String name, String password) {
        String[] u;
        synchronized (this) { u = name == null ? null : users.get(name.toLowerCase()); }
        String[] ref = u == null ? new String[]{null, dummy[0], dummy[1], dummy[2]} : u;
        String[] h = hash(password == null ? "" : password, Base64.getDecoder().decode(ref[2]), Integer.parseInt(ref[1]));
        boolean ok = MessageDigest.isEqual(h[2].getBytes(StandardCharsets.UTF_8), ref[3].getBytes(StandardCharsets.UTF_8));
        return u != null && ok ? u[0] : null;
    }

    public String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        return sb.toString();
    }

    // ---- storage ------------------------------------------------------------------------------

    private byte[] newSalt() {
        byte[] s = new byte[16];
        random.nextBytes(s);
        return s;
    }

    /** @return {iterations, salt, hash}, the last two Base64 encoded */
    private static String[] hash(String password, byte[] salt, int iterations) {
        try {
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded();
            return new String[]{String.valueOf(iterations), Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(h)};
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split(":");
                if (p.length == 4 && validName(p[0])) users.put(p[0].toLowerCase(), p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    private void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        List<String> lines = new ArrayList<>();
        users.values().forEach(u -> lines.add(String.join(":", u)));
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }
}

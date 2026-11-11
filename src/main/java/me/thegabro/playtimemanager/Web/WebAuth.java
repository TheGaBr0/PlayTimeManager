package me.thegabro.playtimemanager.Web;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Username and password authentication for the web dashboard. A successful login yields a
 * random session token, which the server hands to the browser as an HttpOnly cookie.
 * Sessions live in memory: a restart logs everybody out.
 */
public class WebAuth {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 5 * 60_000L;

    private final SecureRandom random = new SecureRandom();
    private final long sessionTtlMs;
    private final WebUsers users;

    public record Session(String user, String ip, long created, long expires) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, long[]> rate = new ConcurrentHashMap<>(); // key -> {windowStart, count}
    private final Map<String, long[]> failures = new ConcurrentHashMap<>(); // user -> {count, lockedUntil}

    public WebAuth(int sessionHours, WebUsers users) {
        this.sessionTtlMs = Math.max(1, sessionHours) * 3_600_000L;
        this.users = users;
    }

    public WebUsers getUsers() { return users; }

    /** Fixed-window limiter. Returns true when the call is allowed. */
    public boolean allow(String bucket, String ip, int maxPerMinute) {
        long now = System.currentTimeMillis();
        long[] w = rate.compute(bucket + "|" + ip, (k, v) ->
                (v == null || now - v[0] > 60_000L) ? new long[]{now, 1} : new long[]{v[0], v[1] + 1});
        if (rate.size() > 10_000) rate.entrySet().removeIf(e -> now - e.getValue()[0] > 60_000L);
        return w[1] <= maxPerMinute;
    }

    /** Whether an account is temporarily locked after too many wrong passwords. */
    public boolean isLocked(String user) {
        long[] f = user == null ? null : failures.get(user.toLowerCase());
        return f != null && f[1] > System.currentTimeMillis();
    }

    /** Returns a new session token, or null when the credentials are wrong or the account is locked. */
    public String login(String user, String password, String ip) {
        if (user == null || isLocked(user)) return null;
        String key = user.toLowerCase();
        String name = users.verify(user, password);
        if (name == null) {
            failures.compute(key, (k, f) -> {
                long count = (f == null || f[1] != 0 && f[1] < System.currentTimeMillis() ? 0 : f[0]) + 1;
                return new long[]{count, count >= MAX_FAILURES ? System.currentTimeMillis() + LOCK_MS : 0};
            });
            if (failures.size() > 10_000) failures.entrySet().removeIf(e -> e.getValue()[1] < System.currentTimeMillis() && e.getValue()[0] < 2);
            return null;
        }
        failures.remove(key);
        purge();
        String token = token(32);
        long now = System.currentTimeMillis();
        sessions.put(token, new Session(name, ip, now, now + sessionTtlMs));
        return token;
    }

    public Session validate(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.expires() < System.currentTimeMillis()) { sessions.remove(token); return null; }
        // an account removed from the file loses its sessions at once
        if (!users.exists(s.user())) { sessions.remove(token); return null; }
        return s;
    }

    public void logout(String token) { if (token != null) sessions.remove(token); }

    public void logoutAll() { sessions.clear(); }

    /** Revokes every session of one account. */
    public void logoutUser(String user) {
        sessions.values().removeIf(s -> s.user().equalsIgnoreCase(user));
    }

    public List<Session> activeSessions() {
        purge();
        return new ArrayList<>(sessions.values());
    }

    private void purge() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> s.expires() < now);
    }

    private String token(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}

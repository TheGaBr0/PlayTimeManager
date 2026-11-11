package me.thegabro.playtimemanager.Web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class WebAuthTest {

    @TempDir File dir;

    private WebAuth auth() throws Exception {
        WebUsers users = new WebUsers(new File(dir, "web-users.txt"));
        assertTrue(users.set("Admin", "correct-horse"));
        return new WebAuth(1, users);
    }

    @Test
    void correctPasswordGivesSessionAndNameIsCaseInsensitive() throws Exception {
        WebAuth auth = auth();
        String token = auth.login("admin", "correct-horse", "1.2.3.4");
        assertNotNull(token);
        assertEquals("Admin", auth.validate(token).user());
    }

    @Test
    void wrongPasswordUnknownUserAndBadTokenAreRejected() throws Exception {
        WebAuth auth = auth();
        assertNull(auth.login("Admin", "wrong-pass", "1.2.3.4"));
        assertNull(auth.login("ghost", "correct-horse", "1.2.3.4"));
        assertNull(auth.login(null, null, "1.2.3.4"));
        assertNull(auth.validate("nope"));
        assertNull(auth.validate(null));
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        WebAuth auth = auth();
        for (int i = 0; i < 5; i++) assertNull(auth.login("Admin", "bad-pass-" + i, "1.2.3.4"));
        assertTrue(auth.isLocked("admin"));
        assertNull(auth.login("Admin", "correct-horse", "1.2.3.4"));
    }

    @Test
    void successResetsFailureCount() throws Exception {
        WebAuth auth = auth();
        for (int i = 0; i < 4; i++) auth.login("Admin", "bad-pass", "ip");
        assertNotNull(auth.login("Admin", "correct-horse", "ip"));
        for (int i = 0; i < 4; i++) auth.login("Admin", "bad-pass", "ip");
        assertFalse(auth.isLocked("Admin"));
    }

    @Test
    void logoutRevokesSessions() throws Exception {
        WebAuth auth = auth();
        String t1 = auth.login("Admin", "correct-horse", "ip");
        String t2 = auth.login("Admin", "correct-horse", "ip");
        auth.logout(t1);
        assertNull(auth.validate(t1));
        assertNotNull(auth.validate(t2));
        auth.logoutUser("admin");
        assertNull(auth.validate(t2));
    }

    @Test
    void removedAccountLosesItsSessions() throws Exception {
        WebAuth auth = auth();
        String token = auth.login("Admin", "correct-horse", "ip");
        assertTrue(auth.getUsers().remove("admin"));
        assertNull(auth.validate(token));
    }

    @Test
    void passwordsAreStoredHashedAndSurviveReload() throws Exception {
        WebAuth auth = auth();
        File f = new File(dir, "web-users.txt");
        assertFalse(Files.readString(f.toPath()).contains("correct-horse"));
        WebUsers reloaded = new WebUsers(f);
        assertEquals("Admin", reloaded.verify("ADMIN", "correct-horse"));
        assertNull(reloaded.verify("Admin", "other-pass"));
    }

    @Test
    void invalidNamesAndShortPasswordsAreRefused() throws Exception {
        WebUsers users = new WebUsers(new File(dir, "u.txt"));
        assertFalse(users.set("bad:name", "long-enough"));
        assertFalse(users.set("ok", "short"));
        assertEquals(16, users.generatePassword().length());
        assertTrue(users.set("ok", users.generatePassword()));
    }

    @Test
    void rateLimiterBlocksAfterLimit() throws Exception {
        WebAuth auth = auth();
        for (int i = 0; i < 3; i++) assertTrue(auth.allow("x", "ip", 3));
        assertFalse(auth.allow("x", "ip", 3));
        assertTrue(auth.allow("x", "other", 3));
    }
}

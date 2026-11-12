package me.thegabro.playtimemanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** The web dashboard: password login, read-only API and request filtering. */
@Tag("web")
class WebDashboardTest extends PluginTestBase {

    private static final int PORT = freePort();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base = "http://127.0.0.1:" + PORT;

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    protected void customizeConfig(YamlConfiguration config) {
        config.set("web.enabled", true);
        config.set("web.port", PORT);
    }

    private HttpResponse<String> send(String method, String path, String body, String cookie, String origin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) b.header("Cookie", cookie);
        if (origin != null) b.header("Origin", origin);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login() throws Exception {
        String out = run(server.getConsoleSender(), "playtimeweb", "adduser", "admin", "correct-horse");
        assertTrue(out.contains("Account created"), out);

        HttpResponse<String> bad = send("POST", "/api/auth/login", "{\"username\":\"admin\",\"password\":\"wrong-pass\"}", null, base);
        assertEquals(401, bad.statusCode());
        assertTrue(bad.headers().firstValue("Set-Cookie").isEmpty(), "no cookie on failure");

        HttpResponse<String> ok = send("POST", "/api/auth/login", "{\"username\":\"Admin\",\"password\":\"correct-horse\"}", null, base);
        assertEquals(200, ok.statusCode());
        String setCookie = ok.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict"), setCookie);
        return setCookie.split(";")[0];
    }

    private String login2() throws Exception {
        HttpResponse<String> ok = send("POST", "/api/auth/login", "{\"username\":\"admin\",\"password\":\"correct-horse\"}", null, base);
        assertEquals(200, ok.statusCode());
        return ok.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
    }

    @Test
    void passwordLoginThenReadOnlyApi() throws Exception {
        PlayerMock player = server.addPlayer("WebPlayer");
        settle();
        player.disconnect();
        settle();

        assertEquals(401, send("GET", "/api/players", null, null, null).statusCode(), "no session, no data");
        assertEquals(200, send("GET", "/", null, null, null).statusCode(), "the page itself is public");
        assertEquals("image/webp", send("GET", "/icon.webp", null, null, null).headers().firstValue("Content-Type").orElse(""));

        String cookie = login();

        JsonObject players = JsonParser.parseString(send("GET", "/api/players?search=webpl", null, cookie, null).body()).getAsJsonObject();
        JsonArray rows = players.getAsJsonArray("rows");
        assertEquals(1, rows.size());
        String uuid = rows.get(0).getAsJsonObject().get("uuid").getAsString();

        JsonObject detail = JsonParser.parseString(send("GET", "/api/player/" + uuid, null, cookie, null).body()).getAsJsonObject();
        assertEquals("WebPlayer", detail.get("nickname").getAsString());
        assertEquals(1, detail.getAsJsonArray("sessions").size(), "the journal session is exposed");
        assertTrue(detail.get("afk").isJsonPrimitive(), "afk is the AFK playtime, not the intervals");
        assertTrue(detail.get("afkIntervals").isJsonArray() && detail.get("vanishIntervals").isJsonArray());

        JsonObject daily = JsonParser.parseString(send("GET", "/api/player/" + uuid + "/daily?days=30", null, cookie, null).body()).getAsJsonObject();
        assertEquals(30, daily.getAsJsonArray("days").size(), "default window is 30 days");
        assertEquals(1, daily.get("totalDays").getAsInt());
        JsonObject allDays = JsonParser.parseString(send("GET", "/api/player/" + uuid + "/daily?days=0", null, cookie, null).body()).getAsJsonObject();
        assertEquals(1, allDays.getAsJsonArray("days").size(), "days=0 starts at the first recorded session");
        assertEquals(400, send("GET", "/api/player/xyz/daily", null, cookie, null).statusCode());
        assertEquals(404, send("GET", "/api/player/00000000-0000-0000-0000-000000000000/daily", null, cookie, null).statusCode());

        JsonObject overview = JsonParser.parseString(send("GET", "/api/overview", null, cookie, null).body()).getAsJsonObject();
        JsonObject jr = overview.getAsJsonObject("journal");
        assertEquals(672, jr.getAsJsonArray("heat").size(), "weekday x 15-minute slots");
        assertEquals(7, jr.getAsJsonArray("lengths").size());
        assertEquals(30, jr.getAsJsonArray("onlinePerDay").size());
        assertEquals(30, overview.getAsJsonArray("activity").size());
        assertEquals(200, send("GET", "/api/goals", null, cookie, null).statusCode());
        JsonObject rewards = JsonParser.parseString(send("GET", "/api/rewards", null, cookie, null).body()).getAsJsonObject();
        assertTrue(rewards.get("rewards").isJsonArray() && rewards.get("pending").isJsonArray());
        assertFalse(JsonParser.parseString(send("GET", "/api/goals", null, cookie, null).body()).getAsJsonObject().has("pending"),
                "rewards live in their own endpoint");

        // a goal that only exists in the history is still listed, and opens with who completed it
        me.thegabro.playtimemanager.Database.DatabaseHandler.getInstance().getGoalsDAO()
                .addCompletedGoal(uuid, "WebPlayer", "Old Goal", true);
        JsonObject goals = JsonParser.parseString(send("GET", "/api/goals", null, cookie, null).body()).getAsJsonObject();
        JsonObject old = null;
        for (var e : goals.getAsJsonArray("goals")) if (e.getAsJsonObject().get("name").getAsString().equals("Old Goal")) old = e.getAsJsonObject();
        assertNotNull(old, "history-only goal listed");
        assertFalse(old.get("configured").getAsBoolean());
        assertEquals(1, old.get("completed").getAsLong());
        JsonObject gd = JsonParser.parseString(send("GET", "/api/goal/Old%20Goal", null, cookie, null).body()).getAsJsonObject();
        assertEquals(1, gd.getAsJsonArray("completions").size());
        assertEquals("WebPlayer", gd.getAsJsonArray("completions").get(0).getAsJsonObject().get("nickname").getAsString());
        // a goal without a playtime requirement reports no time instead of Long.MAX_VALUE
        var goal = new me.thegabro.playtimemanager.Goals.Goal(plugin, "NoTimeGoal");
        me.thegabro.playtimemanager.Goals.GoalsManager.getInstance().addGoal(goal);
        JsonObject noTime = JsonParser.parseString(send("GET", "/api/goal/NoTimeGoal", null, cookie, null).body()).getAsJsonObject();
        assertTrue(noTime.get("configured").getAsBoolean());
        assertFalse(noTime.has("time"), "no requirement: the field is absent, shown as 'Not set'");
        me.thegabro.playtimemanager.Goals.GoalsManager.getInstance().removeGoal(goal);
        assertEquals(404, send("GET", "/api/goal/NoSuchGoal", null, cookie, null).statusCode());
        assertEquals(400, send("GET", "/api/player/not-a-uuid%27%3B--", null, cookie, null).statusCode());
        assertEquals(405, send("DELETE", "/api/players", null, cookie, null).statusCode(), "read-only");

        // Search text is only ever bound as a parameter
        assertEquals(200, send("GET", "/api/players?search=%27%20OR%201%3D1--&sort=playtime;DROP", null, cookie, null).statusCode());

        assertEquals("admin", JsonParser.parseString(send("GET", "/api/me", null, cookie, null).body()).getAsJsonObject().get("user").getAsString());
        run(server.getConsoleSender(), "playtimeweb", "logout", "admin");
        assertEquals(401, send("GET", "/api/players", null, cookie, null).statusCode(), "logout revokes the session");

        String again = login2();
        run(server.getConsoleSender(), "playtimeweb", "removeuser", "admin");
        assertEquals(401, send("GET", "/api/players", null, again, null).statusCode(), "a removed account loses its sessions");
    }

    @Test
    void rejectsForeignHostAndForeignOrigin() throws Exception {
        // java.net.http forbids overriding Host, so go through a raw socket (DNS rebinding simulation)
        try (var sock = new java.net.Socket("127.0.0.1", PORT)) {
            sock.getOutputStream().write(("GET / HTTP/1.1\r\nHost: evil.example.com\r\nConnection: close\r\n\r\n").getBytes());
            String resp = new String(sock.getInputStream().readAllBytes());
            assertTrue(resp.startsWith("HTTP/1.1 403"), resp);
        }

        assertEquals(403, send("POST", "/api/auth/login", "{}", null, "http://evil.example.com").statusCode(), "cross-origin POST");
        assertEquals(403, send("POST", "/api/auth/login", "{}", null, null).statusCode(), "POST without Origin");
    }

    @Test
    void securityHeadersPresentAndShortPasswordRefused() throws Exception {
        String out = run(server.getConsoleSender(), "playtimeweb", "adduser", "bob", "short");
        assertTrue(out.contains("at least"), out);

        HttpResponse<String> page = send("GET", "/", null, null, null);
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));
    }

    @Test
    void repeatedWrongPasswordsLockTheAccount() throws Exception {
        run(server.getConsoleSender(), "playtimeweb", "adduser", "carol", "correct-horse");
        for (int i = 0; i < 5; i++) {
            assertEquals(401, send("POST", "/api/auth/login", "{\"username\":\"carol\",\"password\":\"nope-nope\"}", null, base).statusCode());
        }
        assertEquals(429, send("POST", "/api/auth/login", "{\"username\":\"carol\",\"password\":\"correct-horse\"}", null, base).statusCode(),
                "even the right password is refused while locked");
    }

    @Test
    void frontendNeverUsesInlineStyleAttributes() throws Exception {
        // The CSP has no 'unsafe-inline': a style attribute written as markup or through setAttribute is dropped
        String js = send("GET", "/app.js", null, null, null).body();
        assertFalse(js.contains("setAttribute('style'"), "use element.style instead");
        assertFalse(js.contains(".innerHTML"), "DOM must be built from text only");
    }
}

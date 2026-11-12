package me.thegabro.playtimemanager.Web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Minimal read-only HTTP dashboard built on the JDK HTTP server (no extra dependencies).
 * Bound to loopback by default; authentication is handled by {@link WebAuth}.
 */
public class WebServer {

    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F-]{32,36}$");
    private static final Map<String, String> STATIC = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/app.js", "app.js",
            "/style.css", "style.css",
            "/icon.webp", "icon.webp");
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "webp", "image/webp");
    private static final String COOKIE = "ptm_session";

    private final PlayTimeManager plugin;
    private final Gson gson = new Gson();
    private final WebAuth auth;
    private final WebDAO dao;
    // The overview is server-wide and costly; per-player trends are only computed when someone opens that player.
    private final TtlCache<String, Map<String, Object>> overviewCache = new TtlCache<>(30_000, 4);
    private final TtlCache<String, Map<String, Object>> dailyCache = new TtlCache<>(60_000, 500);
    private final boolean trustProxy;
    private final boolean loopbackOnly;
    private final Set<String> allowedHosts = new HashSet<>();
    private final boolean secureCookie;
    private final int sessionSeconds;
    private final Map<String, byte[]> assets = new HashMap<>();

    private HttpServer server;
    private ExecutorService executor;

    public WebServer(PlayTimeManager plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfiguration();
        int hours = Math.max(1, cfg.getInt("web.session-hours", 12));
        this.sessionSeconds = hours * 3600;
        this.auth = new WebAuth(hours, new WebUsers(new java.io.File(plugin.getDataFolder(), "web-users.txt")));
        this.dao = new WebDAO(DatabaseHandler.getInstance());
        this.trustProxy = cfg.getBoolean("web.trust-proxy-headers", false);
        this.secureCookie = cfg.getBoolean("web.secure-cookie", false);

        String bind = cfg.getString("web.bind-address", "127.0.0.1");
        boolean loop;
        try { loop = InetAddress.getByName(bind).isLoopbackAddress(); } catch (IOException e) { loop = false; }
        this.loopbackOnly = loop;
        for (String h : cfg.getStringList("web.allowed-hosts", new ArrayList<>())) {
            allowedHosts.add(h.toLowerCase());
        }
    }

    public WebAuth getAuth() { return auth; }

    public void start() throws IOException {
        for (String f : new HashSet<>(STATIC.values())) {
            try (InputStream in = plugin.getResource("web/" + f)) {
                if (in == null) throw new IOException("Missing bundled web resource: " + f);
                assets.put(f, in.readAllBytes());
            }
        }
        var cfg = plugin.getConfiguration();
        String bind = cfg.getString("web.bind-address", "127.0.0.1");
        int port = cfg.getInt("web.port", 8765);

        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 32);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "PTM-Web");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();

        plugin.getLogger().info("Web dashboard listening on http://" + bind + ":" + port
                + (loopbackOnly ? " (local access only)" : " (exposed: put it behind HTTPS!)"));
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        auth.logoutAll();
    }

    // ---- request handling ---------------------------------------------------------------------

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!hostAllowed(ex)) { send(ex, 403, "text/plain", "Forbidden"); return; }

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            String ip = clientIp(ex);

            if (path.startsWith("/api/")) {
                if (!auth.allow("api", ip, 240)) { json(ex, 429, Map.of("error", "rate_limited")); return; }
                // CSRF / cross-origin: state-changing calls must come from our own origin
                if (method.equals("POST") && !originOk(ex)) { json(ex, 403, Map.of("error", "bad_origin")); return; }
                api(ex, path, method, ip);
                return;
            }

            String file = STATIC.get(path);
            if (file == null || !(method.equals("GET") || method.equals("HEAD"))) {
                send(ex, 404, "text/plain", "Not found");
                return;
            }
            String ext = file.substring(file.lastIndexOf('.') + 1);
            send(ex, 200, TYPES.get(ext), assets.get(file));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Web dashboard error", e);
            try { json(ex, 500, Map.of("error", "internal")); } catch (Exception ignored) { }
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path, String method, String ip) throws IOException {
        // ---- unauthenticated login endpoint
        if (path.equals("/api/auth/login") && method.equals("POST")) {
            if (!auth.allow("login", ip, 10)) { json(ex, 429, Map.of("error", "rate_limited")); return; }
            JsonObject body = readJson(ex);
            String user = jsonString(body, "username"), pass = jsonString(body, "password");
            if (user == null || pass == null) { json(ex, 400, Map.of("error", "bad_request")); return; }
            if (auth.isLocked(user)) { json(ex, 429, Map.of("error", "locked")); return; }
            String token = auth.login(user, pass, ip);
            if (token == null) {
                plugin.getLogger().warning("[Web] Failed dashboard login for '" + user + "' from " + ip);
                json(ex, 401, Map.of("error", "invalid_credentials"));
                return;
            }
            ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token
                    + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + sessionSeconds
                    + (secureCookie ? "; Secure" : ""));
            json(ex, 200, Map.of("ok", true));
            return;
        }

        // ---- everything below needs a valid session
        String token = cookie(ex, COOKIE);
        WebAuth.Session session = auth.validate(token);
        if (path.equals("/api/auth/logout") && method.equals("POST")) {
            auth.logout(token);
            ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
            json(ex, 200, Map.of("ok", true));
            return;
        }
        if (session == null) { json(ex, 401, Map.of("error", "unauthorized")); return; }
        if (!method.equals("GET")) { json(ex, 405, Map.of("error", "read_only")); return; }

        Map<String, String> q = query(ex);
        try {
            switch (path) {
                case "/api/me" -> json(ex, 200, Map.of("user", session.user(), "expires", session.expires()));
                case "/api/overview" -> json(ex, 200, overviewCache.get("overview", dao::overview));
                case "/api/goals" -> json(ex, 200, goalsOverview());
                case "/api/rewards" -> json(ex, 200, rewardsOverview());
                case "/api/players/online" -> json(ex, 200, dao.online(intParam(q, "tz", 0)));
                case "/api/players" -> json(ex, 200, dao.players(
                        q.get("search"), q.getOrDefault("sort", "playtime"),
                        !"asc".equals(q.get("dir")), intParam(q, "page", 0), intParam(q, "size", 25)));
                default -> {
                    if (path.startsWith("/api/goal/")) {
                        String name = URLDecoder.decode(path.substring("/api/goal/".length()), StandardCharsets.UTF_8);
                        Map<String, Object> g = goalDetail(name);
                        if (g == null) json(ex, 404, Map.of("error", "not_found"));
                        else json(ex, 200, g);
                    } else if (path.startsWith("/api/reward/")) {
                        int id;
                        try { id = Integer.parseInt(path.substring("/api/reward/".length())); }
                        catch (NumberFormatException e) { json(ex, 400, Map.of("error", "bad_id")); return; }
                        Map<String, Object> r = rewardDetail(id);
                        if (r == null) json(ex, 404, Map.of("error", "not_found"));
                        else json(ex, 200, r);
                    } else if (path.startsWith("/api/player/") && path.endsWith("/daily")) {
                        String uuid = path.substring("/api/player/".length(), path.length() - "/daily".length());
                        if (!UUID_RE.matcher(uuid).matches()) { json(ex, 400, Map.of("error", "bad_uuid")); return; }
                        int days = intParam(q, "days", 30), tz = intParam(q, "tz", 0);
                        Map<String, Object> p = dailyCache.get(uuid + "|" + days + "|" + tz, () -> dao.playerDaily(uuid, days, tz));
                        if (p == null) json(ex, 404, Map.of("error", "not_found"));
                        else json(ex, 200, p);
                    } else if (path.startsWith("/api/player/")) {
                        String uuid = path.substring("/api/player/".length());
                        if (!UUID_RE.matcher(uuid).matches()) { json(ex, 400, Map.of("error", "bad_uuid")); return; }
                        Map<String, Object> p = dao.player(uuid);
                        if (p == null) json(ex, 404, Map.of("error", "not_found"));
                        else json(ex, 200, p);
                    } else {
                        json(ex, 404, Map.of("error", "not_found"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Web dashboard query failed", e);
            json(ex, 500, Map.of("error", "database"));
        }
    }

    // ---- goals --------------------------------------------------------------------------------

    /** Every configured goal plus the ones that only survive in the history, with completion counts. */
    private Map<String, Object> goalsOverview() throws SQLException {
        Map<String, long[]> counts = dao.goalCounts();
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Goal g : GoalsManager.getInstance().getGoals()) {
            seen.add(g.getName());
            long[] c = counts.getOrDefault(g.getName(), new long[]{0, 0});
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", g.getName());
            m.put("configured", true);
            m.put("active", g.isActive());
            m.put("repeatable", g.isRepeatable());
            m.put("time", requiredTime(g));
            m.put("completed", c[0]);
            m.put("players", c[1]);
            list.add(m);
        }
        counts.forEach((name, c) -> {
            if (seen.add(name)) {
                list.add(Map.of("name", name, "configured", false, "active", false, "repeatable", false,
                        "time", 0L, "completed", c[0], "players", c[1]));
            }
        });
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase()));
        return Map.of("goals", list);
    }

    /** Required playtime in ticks, or null when the goal has none (the default is Long.MAX_VALUE). */
    private static Long requiredTime(Goal g) {
        long t = g.getRequirements().getTime();
        return t == Long.MAX_VALUE ? null : t;
    }

    private Map<String, Object> goalDetail(String name) throws SQLException {
        Goal g = GoalsManager.getInstance().getGoal(name);
        Map<String, long[]> counts = dao.goalCounts();
        if (g == null && !counts.containsKey(name)) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("configured", g != null);
        if (g != null) {
            out.put("active", g.isActive());
            out.put("repeatable", g.isRepeatable());
            out.put("perPlayerCheck", g.isPerPlayerCheck());
            out.put("offlineRewards", g.areOfflineRewardsEnabled());
            out.put("time", requiredTime(g));
            out.put("requiredPermissions", new ArrayList<>(g.getRequirements().getPermissions()));
            out.put("placeholderConditions", new ArrayList<>(g.getRequirements().getPlaceholderConditions()));
            out.put("rewardCommands", new ArrayList<>(g.getRewardCommands()));
            out.put("rewardPermissions", new ArrayList<>(g.getRewardPermissions()));
            out.put("message", g.getGoalMessage() == null ? "" : Utils.stripColor(g.getGoalMessage()));
            out.put("sound", g.getGoalSound() == null ? "" : g.getGoalSound());
            out.put("checkEvery", g.getCompletionCheckInterval() == null ? "" : g.getCompletionCheckInterval());
            out.put("usingCron", g.isUsingCronExpression());
            try {
                Map<String, Object> sched = g.getNextSchedule();
                Object next = sched.get("nextCheck");
                if (next instanceof Date d) out.put("nextCheck", d.getTime());
                Object text = sched.get("timeCheckToText");
                if (text != null) out.put("checkText", String.valueOf(text));
            } catch (Exception ignored) { /* schedule is informative only */ }
        }
        long[] c = counts.getOrDefault(name, new long[]{0, 0});
        out.put("completed", c[0]);
        out.put("players", c[1]);
        out.put("completions", dao.goalCompletions(name));
        return out;
    }

    // ---- join streak rewards ------------------------------------------------------------------

    private static String joinsText(JoinStreakReward r) { return r.getRequiredJoinsDisplay(); }

    /** Every configured reward plus the ones that only survive in the history, with their counts. */
    private Map<String, Object> rewardsOverview() throws SQLException {
        Map<Integer, long[]> counts = dao.rewardCounts();
        List<Map<String, Object>> list = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JoinStreakReward r : RewardRegistry.getInstance().getRewards()) {
            seen.add(r.getId());
            long[] c = counts.getOrDefault(r.getId(), new long[3]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("configured", true);
            m.put("joins", joinsText(r));
            m.put("repeatable", r.isRepeatable());
            m.put("received", c[0]);
            m.put("players", c[1]);
            m.put("pending", c[2]);
            list.add(m);
        }
        counts.forEach((id, c) -> {
            if (seen.add(id)) {
                list.add(Map.of("id", id, "configured", false, "joins", "—", "repeatable", false,
                        "received", c[0], "players", c[1], "pending", c[2]));
            }
        });
        list.sort(Comparator.comparingInt(m -> (Integer) m.get("id")));
        return Map.of("rewards", list, "pending", dao.pendingRewards());
    }

    private Map<String, Object> rewardDetail(int id) throws SQLException {
        JoinStreakReward r = RewardRegistry.getInstance().getReward(id);
        Map<Integer, long[]> counts = dao.rewardCounts();
        if (r == null && !counts.containsKey(id)) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("configured", r != null);
        if (r != null) {
            out.put("joins", joinsText(r));
            out.put("singleJoin", r.isSingleJoinReward());
            out.put("repeatable", r.isRepeatable());
            out.put("commands", new ArrayList<>(r.getCommands()));
            out.put("permissions", new ArrayList<>(r.getPermissions()));
            out.put("message", r.getRewardMessage() == null ? "" : Utils.stripColor(r.getRewardMessage()));
            out.put("sound", r.getRewardSound() == null ? "" : r.getRewardSound());
            out.put("description", r.getDescription() == null ? "" : Utils.stripColor(r.getDescription()));
            out.put("rewardDescription", r.getRewardDescription() == null ? "" : Utils.stripColor(r.getRewardDescription()));
            out.put("icon", r.getItemIcon() == null ? "" : r.getItemIcon());
        }
        long[] c = counts.getOrDefault(id, new long[3]);
        out.put("received", c[0]);
        out.put("players", c[1]);
        out.put("pending", c[2]);
        out.put("receivers", dao.rewardReceivers(id));
        out.put("waiting", dao.rewardPending(id));
        return out;
    }

    // ---- security helpers ---------------------------------------------------------------------

    /** Blocks DNS-rebinding: a loopback-bound server only answers to local host names. */
    private boolean hostAllowed(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) return false;
        host = host.toLowerCase();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < colon) host = host.substring(0, colon);
        if (allowedHosts.contains(host)) return true;
        if (!loopbackOnly) return allowedHosts.isEmpty();
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
    }

    private boolean originOk(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return false; // browsers always send Origin on POST
        String host = ex.getRequestHeaders().getFirst("Host");
        return host != null && (origin.equals("http://" + host) || origin.equals("https://" + host));
    }

    private String clientIp(HttpExchange ex) {
        if (trustProxy) {
            String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private String cookie(HttpExchange ex, String name) {
        List<String> hs = ex.getRequestHeaders().get("Cookie");
        if (hs == null) return null;
        for (String h : hs) {
            for (String part : h.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
        }
        return null;
    }

    // ---- io helpers ---------------------------------------------------------------------------

    private JsonObject readJson(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            byte[] b = in.readNBytes(2048);
            return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonString(JsonObject o, String key) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return m;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }

    private int intParam(Map<String, String> q, String key, int def) {
        try { return Integer.parseInt(q.getOrDefault(key, "")); } catch (NumberFormatException e) { return def; }
    }

    private void json(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", gson.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int status, String type, String body) throws IOException {
        send(ex, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Cross-Origin-Resource-Policy", "same-origin");
        h.set("Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; " +
                        "img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) ex.getResponseBody().write(body);
    }
}

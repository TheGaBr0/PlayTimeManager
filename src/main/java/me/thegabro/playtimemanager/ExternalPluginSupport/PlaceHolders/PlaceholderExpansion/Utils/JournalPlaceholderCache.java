package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Journal.JournalManager;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Journal values for placeholders. PlaceholderAPI asks on the main thread, so the database is never
 * read there: a value is served from memory and refreshed in the background once it is older than
 * the TTL. Until the first load finishes the value is null ("Loading...").
 */
public class JournalPlaceholderCache {

    /** An entry older than this many TTLs is too old to show: it reads as loading until refreshed. */
    private static final int EXPIRY_FACTOR = 10;
    private static final int TOP_SIZE = 100;
    private static final int MAX_ENTRIES = 2000;

    /** Time spent in a window, in milliseconds, with vanished time already removed. */
    public record Stats(long online, long afk) {}

    public record Ranked(String uuid, String nickname, long online, long afk) {}

    /** Players ordered best first, once by playtime and once by AFK playtime. */
    public record Top(List<Ranked> byOnline, List<Ranked> byAfk) {}

    private static final class Entry<T> {
        volatile T value;
        volatile long loadedAt;
        final AtomicBoolean refreshing = new AtomicBoolean();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PTM-Placeholder-Journal");
        t.setDaemon(true);
        return t;
    });
    private final long userTtlMs = ttlMillis("placeholders.journal-cache-seconds", 10);
    private final long topTtlMs = ttlMillis("placeholders.journal-top-cache-seconds", 30);
    private final Map<String, Entry<Stats>> users = new ConcurrentHashMap<>();
    private final Map<Long, Entry<Top>> tops = new ConcurrentHashMap<>();

    /** @return the cached stats of the player in the last {@code windowMillis}, or null while loading */
    public Stats user(String uuid, long windowMillis) {
        if (users.size() > MAX_ENTRIES) prune(users, userTtlMs);
        Entry<Stats> entry = users.computeIfAbsent(uuid + "|" + windowMillis, k -> new Entry<>());
        return read(entry, userTtlMs, () -> {
            long to = System.currentTimeMillis();
            JournalReport r = JournalReport.build(dao(), uuid, to - windowMillis, to);
            return new Stats(r.onlineVisible(), r.afkVisible());
        });
    }

    /** @return the cached leaderboard of the last {@code windowMillis}, or null while loading */
    public Top top(long windowMillis) {
        if (tops.size() > MAX_ENTRIES) prune(tops, topTtlMs);
        return read(tops.computeIfAbsent(windowMillis, k -> new Entry<>()), topTtlMs, () -> loadTop(windowMillis));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private <T> T read(Entry<T> entry, long ttl, Supplier<T> loader) {
        T value = entry.value;
        long age = System.currentTimeMillis() - entry.loadedAt;
        if (value == null || age > ttl) {
            refresh(entry, loader);
        }
        // Slightly stale data is fine while the refresh runs, very stale data is not
        return value != null && age > ttl * EXPIRY_FACTOR ? null : value;
    }

    private static long ttlMillis(String path, int defaultSeconds) {
        Integer seconds = PlayTimeManager.getInstance().getConfiguration().getInt(path, defaultSeconds);
        return Math.max(1, seconds == null ? defaultSeconds : seconds) * 1000L;
    }

    private <T> void refresh(Entry<T> entry, Supplier<T> loader) {
        if (!entry.refreshing.compareAndSet(false, true)) return;
        try {
            // Flushing first makes the sessions open right now count up to this moment
            JournalManager.getInstance().flush().thenRunAsync(() -> {
                try {
                    entry.value = loader.get();
                    entry.loadedAt = System.currentTimeMillis();
                } catch (Exception e) {
                    PlayTimeManager.getInstance().getLogger().log(Level.WARNING,
                            "Could not load journal data for a placeholder", e);
                } finally {
                    entry.refreshing.set(false);
                }
            }, executor);
        } catch (RuntimeException e) {
            entry.refreshing.set(false);
        }
    }

    private <K, T> void prune(Map<K, Entry<T>> map, long ttl) {
        long limit = System.currentTimeMillis() - ttl * EXPIRY_FACTOR;
        map.entrySet().removeIf(e -> e.getValue().value != null && e.getValue().loadedAt < limit
                && !e.getValue().refreshing.get());
    }

    private Top loadTop(long windowMillis) {
        long to = System.currentTimeMillis();
        Map<String, JournalReport> reports = JournalReport.buildAll(dao(), to - windowMillis, to);
        List<String> hidden = DBUsersManager.getInstance().getPlayersHiddenFromLeaderBoard();

        return new Top(
                rank(reports, hidden, JournalReport::onlineVisible),
                rank(reports, hidden, JournalReport::afkVisible));
    }

    private List<Ranked> rank(Map<String, JournalReport> reports, List<String> hidden,
                              java.util.function.ToLongFunction<JournalReport> metric) {
        List<Map.Entry<String, JournalReport>> sorted = new ArrayList<>(reports.entrySet());
        sorted.removeIf(e -> metric.applyAsLong(e.getValue()) <= 0);
        sorted.sort(Comparator.comparingLong((Map.Entry<String, JournalReport> e) -> metric.applyAsLong(e.getValue())).reversed());

        // Nicknames are read one by one, so stop as soon as the leaderboard is full
        List<Ranked> result = new ArrayList<>();
        for (Map.Entry<String, JournalReport> e : sorted) {
            if (result.size() >= TOP_SIZE) break;
            String nickname = DatabaseHandler.getInstance().getPlayerDAO().getNickname(e.getKey());
            if (nickname == null || hidden.contains(nickname)) continue;
            JournalReport r = e.getValue();
            result.add(new Ranked(e.getKey(), nickname, r.onlineVisible(), r.afkVisible()));
        }
        return result;
    }

    private JournalDAO dao() {
        return DatabaseHandler.getInstance().getJournalDAO();
    }
}

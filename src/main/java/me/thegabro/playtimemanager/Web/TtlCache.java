package me.thegabro.playtimemanager.Web;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small time-based cache for dashboard queries. Entries are computed on first request only, so
 * nothing is loaded for players nobody looks at. Concurrent requests for the same key share one
 * computation instead of each hitting the database.
 */
public final class TtlCache<K, V> {

    @FunctionalInterface
    public interface Loader<V> { V load() throws SQLException; }

    private record Entry<V>(V value, long expires) {}

    private static final class Wrapped extends RuntimeException {
        Wrapped(SQLException cause) { super(cause); }
    }

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxEntries;

    public TtlCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
    }

    /** Returns the cached value, or loads and stores it when missing or older than the TTL. */
    public V get(K key, Loader<V> loader) throws SQLException {
        long now = System.currentTimeMillis();
        if (map.size() >= maxEntries) map.values().removeIf(e -> e.expires <= now);
        if (map.size() >= maxEntries) map.clear();
        try {
            return map.compute(key, (k, old) -> {
                if (old != null && old.expires > now) return old;
                try { return new Entry<>(loader.load(), System.currentTimeMillis() + ttlMs); }
                catch (SQLException e) { throw new Wrapped(e); }
            }).value;
        } catch (Wrapped w) {
            throw (SQLException) w.getCause();
        }
    }

    public void clear() { map.clear(); }
}

package me.thegabro.playtimemanager.Journal;

import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Keeps the playtime journal: one session row per login, plus the AFK and vanish intervals that
 * happened inside it.
 */
public class JournalManager {

    private static JournalManager instance;

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final Map<String, OpenSession> open = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PTM-Journal-Writer");
        thread.setDaemon(true);
        return thread;
    });

    private static final class OpenSession {
        final long login;
        long afkStart = -1;
        long vanishStart = -1;

        OpenSession(long login) {
            this.login = login;
        }
    }

    private JournalManager() {}

    public static synchronized JournalManager getInstance() {
        if (instance == null) {
            instance = new JournalManager();
        }
        return instance;
    }

    /**
     * Opens a session for the player. If one is already open (plugin reload while online) it is
     * kept, and its AFK interval is closed because the recreated OnlineUser starts non-AFK.
     */
    public synchronized void openSession(String uuid) {
        long now = System.currentTimeMillis();
        OpenSession session = open.get(uuid);

        if (session != null) {
            if (session.afkStart >= 0) {
                enqueue(List.of(JournalDAO.updateAfkEnd(uuid, session.afkStart, now)));
                session.afkStart = -1;
            }
            return;
        }

        open.put(uuid, new OpenSession(now));
        enqueue(List.of(JournalDAO.insertSession(uuid, now)));
    }

    /** Closes the session and any interval still open in it, all with the same logout instant. */
    public synchronized void closeSession(String uuid) {
        OpenSession session = open.remove(uuid);
        if (session == null) return;
        enqueue(closeOps(uuid, session, System.currentTimeMillis()));
    }

    public synchronized void afkStarted(String uuid) {
        OpenSession session = open.get(uuid);
        if (session == null || session.afkStart >= 0) return;

        long now = System.currentTimeMillis();
        session.afkStart = now;
        enqueue(List.of(JournalDAO.insertAfk(uuid, session.login, now)));
    }

    public synchronized void afkEnded(String uuid) {
        OpenSession session = open.get(uuid);
        if (session == null || session.afkStart < 0) return;

        enqueue(List.of(JournalDAO.updateAfkEnd(uuid, session.afkStart, System.currentTimeMillis())));
        session.afkStart = -1;
    }

    public synchronized void vanishStarted(String uuid) {
        OpenSession session = open.get(uuid);
        if (session == null || session.vanishStart >= 0) return;

        long now = System.currentTimeMillis();
        session.vanishStart = now;
        enqueue(List.of(JournalDAO.insertVanish(uuid, session.login, now)));
    }

    public synchronized void vanishEnded(String uuid) {
        OpenSession session = open.get(uuid);
        if (session == null || session.vanishStart < 0) return;

        enqueue(List.of(JournalDAO.updateVanishEnd(uuid, session.vanishStart, System.currentTimeMillis())));
        session.vanishStart = -1;
    }

    /**
     * Deletes the journal of one player, or of everybody when {@code uuid} is null. A session that is
     * open right now is not lost: it starts again from this moment (with its AFK and vanish intervals,
     * if the player is AFK or vanished), so what happens next is still recorded.
     *
     * @return completes once the deletion has been written
     */
    public CompletableFuture<Void> reset(String uuid) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        synchronized (this) {
            long now = System.currentTimeMillis();
            List<JournalDAO.Op> ops = new ArrayList<>(uuid == null ? JournalDAO.deleteAll() : JournalDAO.deleteOfPlayer(uuid));

            for (String openUuid : new ArrayList<>(open.keySet())) {
                if (uuid != null && !uuid.equals(openUuid)) continue;

                OpenSession old = open.get(openUuid);
                OpenSession restarted = new OpenSession(now);
                ops.add(JournalDAO.insertSession(openUuid, now));
                if (old.afkStart >= 0) {
                    restarted.afkStart = now;
                    ops.add(JournalDAO.insertAfk(openUuid, now, now));
                }
                if (old.vanishStart >= 0) {
                    restarted.vanishStart = now;
                    ops.add(JournalDAO.insertVanish(openUuid, now, now));
                }
                open.put(openUuid, restarted);
            }

            enqueue(ops);
            try {
                writer.execute(() -> done.complete(null));
            } catch (RejectedExecutionException e) {
                done.complete(null);
            }
        }
        return done;
    }

    /**
     * Moves the end of every open session (and its open intervals) to now, in a single
     * transaction. Called periodically, so a crash loses at most one update interval.
     */
    public synchronized void heartbeat() {
        long now = System.currentTimeMillis();
        List<JournalDAO.Op> ops = new ArrayList<>();

        for (Map.Entry<String, OpenSession> entry : open.entrySet()) {
            String uuid = entry.getKey();
            OpenSession session = entry.getValue();

            ops.add(JournalDAO.updateSessionLogout(uuid, session.login, now));
            if (session.afkStart >= 0) {
                ops.add(JournalDAO.updateAfkEnd(uuid, session.afkStart, now));
            }
            if (session.vanishStart >= 0) {
                ops.add(JournalDAO.updateVanishEnd(uuid, session.vanishStart, now));
            }
        }
        enqueue(ops);
    }

    /**
     * Runs a heartbeat and completes once every write queued so far, including it, has been
     * executed. Used before reading, so the open sessions are reported up to now.
     */
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        synchronized (this) {
            heartbeat();
            try {
                writer.execute(() -> future.complete(null));
            } catch (RejectedExecutionException e) {
                future.complete(null);
            }
        }
        return future;
    }

    /** Closes every open session and waits for the pending writes. Called from onDisable. */
    public void shutdown() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            List<JournalDAO.Op> ops = new ArrayList<>();
            for (Map.Entry<String, OpenSession> entry : open.entrySet()) {
                ops.addAll(closeOps(entry.getKey(), entry.getValue(), now));
            }
            open.clear();
            enqueue(ops);
            writer.shutdown();
        }

        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out while saving the playtime journal");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (JournalManager.class) {
            instance = null;
        }
    }

    private List<JournalDAO.Op> closeOps(String uuid, OpenSession session, long now) {
        List<JournalDAO.Op> ops = new ArrayList<>();
        ops.add(JournalDAO.updateSessionLogout(uuid, session.login, now));
        if (session.afkStart >= 0) {
            ops.add(JournalDAO.updateAfkEnd(uuid, session.afkStart, now));
        }
        if (session.vanishStart >= 0) {
            ops.add(JournalDAO.updateVanishEnd(uuid, session.vanishStart, now));
        }
        return ops;
    }

    private void enqueue(List<JournalDAO.Op> ops) {
        if (ops.isEmpty()) return;
        try {
            writer.execute(() -> {
                try {
                    DatabaseHandler.getInstance().getJournalDAO().executeBatch(ops);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to write the playtime journal", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // The writer is already shut down: the plugin is disabling
        }
    }
}

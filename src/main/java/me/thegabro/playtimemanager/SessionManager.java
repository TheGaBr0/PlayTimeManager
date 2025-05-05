package me.thegabro.playtimemanager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private final Map<UUID, String> activeSessions = new HashMap<>();

    public void createSession(UUID playerId, String token) {
        activeSessions.put(playerId, token);
    }

    public boolean validateSession(UUID playerId, String token) {
        String storedToken = activeSessions.get(playerId);
        return storedToken != null && storedToken.equals(token);
    }

    public void endSession(UUID playerId) {
        activeSessions.remove(playerId);
    }
}
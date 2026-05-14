package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;

public class UserResolver {

    private final DBUsersManager dbUsersManager;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    public UserResolver(DBUsersManager dbUsersManager) {
        this.dbUsersManager = dbUsersManager;
    }

    /**
     * Get user data from cache or trigger async load.
     * Returns the cached user if available, DBUser.LOADING if the async load
     * was just triggered, or DBUser.NOT_FOUND if the user does not exist.
     *
     * For placeholder reads: if the resolved user is a vanished OnlineUser,
     * returns their frozen snapshot so external observers see them as offline.
     */
    public DBUser resolve(String nickname) {
        DBUser cachedUser = dbUsersManager.getUserFromCacheSync(nickname);

        if (cachedUser instanceof OnlineUser ou && onlineUsersManager.isCurrentlyVanished(ou)) {
            DBUser snapshot = onlineUsersManager.getVanishSnapshot(ou.getUuid());
            return snapshot != null ? snapshot : DBUser.LOADING;
        }

        if (cachedUser != null) {
            return cachedUser;
        }

        if (dbUsersManager.isKnownNonExistent(nickname)) {
            return DBUser.NOT_FOUND;
        }

        dbUsersManager.getUserFromNicknameAsync(nickname, user -> {
            if (user == null) {
                dbUsersManager.markAsNonExistent(nickname);
            }
        });

        return DBUser.LOADING;
    }
}
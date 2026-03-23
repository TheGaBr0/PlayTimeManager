package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;

public class UserResolver {

    private final DBUsersManager dbUsersManager;

    public UserResolver(DBUsersManager dbUsersManager) {
        this.dbUsersManager = dbUsersManager;
    }

    /**
     * Get user data from cache or trigger async load.
     * Returns the cached user if available, DBUser.LOADING if the async load
     * was just triggered, or DBUser.NOT_FOUND if the user does not exist.
     */
    public DBUser resolve(String nickname) {
        DBUser cachedUser = dbUsersManager.getUserFromCacheSync(nickname);

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
package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

public class PlayTimePlaceHolders extends PlaceholderExpansion {
    private static final String[] TIME_UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final PlaytimeFormatsConfiguration playtimeFormatsConfiguration = PlaytimeFormatsConfiguration.getInstance();
    private LuckPermsManager luckPermsManager = null;
    private PlaytimeFormat playtimeFormat;

    /**
     * Get user data from cache or trigger async load
     * Returns the cached user if available, triggers async load and returns loading sentinel,
     * or returns not-found sentinel if user doesn't exist
     */
    private DBUser getUserFromCache(String nickname) {
        // Check if user is in cache (synchronous)
        DBUser cachedUser = dbUsersManager.getUserFromCacheSync(nickname);

        if (cachedUser != null) {
            return cachedUser;
        }

        // Check if we already know this user doesn't exist
        if (dbUsersManager.isKnownNonExistent(nickname)) {
            return DBUser.NOT_FOUND;
        }

        // Not in cache - trigger async load (fire and forget)
        dbUsersManager.getUserFromNicknameAsync(nickname, user -> {
            if (user == null) {
                // Mark as non-existent so future calls know immediately
                dbUsersManager.markAsNonExistent(nickname);
            }
            // If user exists, it will be cached by DBUsersManager
            // Next placeholder evaluation will find it
        });

        return DBUser.LOADING;
    }
    private String processParams(String params) {
        // Find the last occurrence of "_"
        int lastUnderscoreIndex = params.lastIndexOf("_");
        if (lastUnderscoreIndex == -1) {
            // Check if there's a colon directly in the params (for cases like "PlayTime:test")
            int colonIndex = params.indexOf(":");
            if (colonIndex != -1) {
                // Extract the format name after the colon
                String formatName = params.substring(colonIndex + 1);
                playtimeFormat = playtimeFormatsConfiguration.getFormat(formatName);
                if (playtimeFormat == null) {
                    playtimeFormat = playtimeFormatsConfiguration.getFormat("default");
                }
                // Return params without the format part
                return params.substring(0, colonIndex);
            }

            playtimeFormat = playtimeFormatsConfiguration.getFormat("default");
            return params; // No underscore found, return original
        }

        // Look for ":" after the last underscore
        int colonIndex = params.indexOf(":", lastUnderscoreIndex);
        if (colonIndex == -1) {
            playtimeFormat = playtimeFormatsConfiguration.getFormat("default");
            return params; // No colon found after last underscore
        }

        // Extract the format name after the colon (everything after ":")
        String formatName = params.substring(colonIndex + 1);
        playtimeFormat = playtimeFormatsConfiguration.getFormat(formatName);

        // If format is not found, use default
        if (playtimeFormat == null) {
            playtimeFormat = playtimeFormatsConfiguration.getFormat("default");
        }

        // Remove the format part (from ":" to the end)
        return params.substring(0, colonIndex);
    }

    public PlayTimePlaceHolders() {
        if (plugin.isPermissionsManagerConfigured()) {
            try {
                this.luckPermsManager = LuckPermsManager.getInstance(plugin);
            } catch (NoClassDefFoundError e) {
                // LuckPerms is not loaded, leave luckPermsManager as null
            }
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "PTM";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TheGabro";
    }

    @Override
    public @NotNull String getVersion() {
        return "3.3.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;

        params = processParams(params);

        // Handle rank placeholder
        if (params.equalsIgnoreCase("rank")) {
            try {
                int position = dbUsersManager.getTopPlayers().indexOf(onlineUsersManager.getOnlineUser(player.getName())) + 1;
                return position != 0 ?
                        String.valueOf(position) : plugin.getConfiguration().getString("placeholders.not-in-leaderboard-message", "-");
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        // Handle first join placeholder
        if (params.equalsIgnoreCase("firstjoin")) {
            try {
                DBUser user = onlineUsersManager.getOnlineUser(player.getName());
                if (user == null) return getErrorMessage("user not found");

                Instant firstJoin = user.getFirstJoin();
                if (firstJoin == null) return getErrorMessage("first join data missing");

                return Utils.formatInstant(firstJoin, plugin.getConfiguration().getString("datetime-format", "MMM dd, yyyy HH:mm:ss"));
            } catch (Exception e) {
                return getErrorMessage("couldn't get first join date");
            }
        }

        // Handle join streak placeholder
        if (params.equalsIgnoreCase("joinstreak")) {
            try {
                return String.valueOf(
                        onlineUsersManager.getOnlineUser(player.getName()).getAbsoluteJoinStreak()
                );
            } catch (Exception e) {
                return getErrorMessage("couldn't get join streak");
            }
        }

        // Handle basic AFK playtime placeholders
        if (params.equalsIgnoreCase("afk_playtime")) {
            try {
                return Utils.ticksToFormattedPlaytime(
                        onlineUsersManager.getOnlineUser(player.getName()).getAFKPlaytime(),
                        playtimeFormat
                );
            } catch (Exception e) {
                return getErrorMessage("couldn't get AFK playtime");
            }
        }

        // Handle unit-specific AFK playtime placeholders
        for (String unit : TIME_UNITS) {
            if (params.equalsIgnoreCase("afk_playtime_" + unit)) {
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(
                            onlineUsersManager.getOnlineUser(player.getName()).getAFKPlaytime(),
                            unit
                    ));
                } catch (Exception e) {
                    return getErrorMessage("couldn't get AFK playtime");
                }
            }
        }

        // Handle basic playtime placeholders
        if (params.equalsIgnoreCase("playtime")) {
            try {
                return Utils.ticksToFormattedPlaytime(
                        onlineUsersManager.getOnlineUser(player.getName()).getPlaytime(),
                        playtimeFormat
                );
            } catch (Exception e) {
                return getErrorMessage("couldn't get playtime");
            }
        }

        // Handle unit-specific playtime placeholders
        for (String unit : TIME_UNITS) {
            if (params.equalsIgnoreCase("playtime_" + unit)) {
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(
                            onlineUsersManager.getOnlineUser(player.getName()).getPlaytime(),
                            unit
                    ));
                } catch (Exception e) {
                    return getErrorMessage("couldn't get playtime");
                }
            }
        }


        String paramLower = params.toLowerCase();

        // Handle rank placeholders
        if (paramLower.startsWith("rank_")) {
            return handleRank(params.substring(5));
        }

        // Handle first join placeholders
        if (paramLower.startsWith("firstjoin_")) {
            return handleFirstJoin(params.substring(10));
        }

        // Handle join streak placeholders
        if (paramLower.startsWith("joinstreak_")) {
            return handleJoinStreak(params.substring(11));
        }

        // Handle LP prefix top
        if (paramLower.startsWith("lp_prefix_top_")) {
            return handleLPPrefixTop(params.substring(14));
        }

        // Handle last seen elapsed top with units
        for (String unit : TIME_UNITS) {
            String prefix = "lastseen_elapsed_top_" + unit.toLowerCase() + "_";
            if (paramLower.startsWith(prefix)) {
                return handleLastSeenElapsedTop(params.substring(prefix.length()), unit);
            }
        }

        // Handle last seen elapsed top
        if (paramLower.startsWith("lastseen_elapsed_top_")) {
            return handleLastSeenElapsedTop(params.substring(21));
        }

        // Handle last seen elapsed with units
        for (String unit : TIME_UNITS) {
            String prefix = "lastseen_elapsed_" + unit.toLowerCase() + "_";
            if (paramLower.startsWith(prefix)) {
                return handleLastSeenElapsed(params.substring(prefix.length()), unit);
            }
        }

        // Handle other placeholders
        if (paramLower.startsWith("lastseen_elapsed_")) {
            return handleLastSeenElapsed(params.substring(17));
        }

        if (paramLower.startsWith("nickname_top_")) {
            return handleNicknameTop(params.substring(13));
        }

        // Handle playtime top with units
        for (String unit : TIME_UNITS) {
            String prefix = "playtime_top_" + unit.toLowerCase() + "_";
            if (paramLower.startsWith(prefix)) {
                return handlePlayTimeTop(params.substring(prefix.length()), unit);
            }
        }

        if (paramLower.startsWith("playtime_top_")) {
            return handlePlayTimeTop(params.substring(13));
        }

        // Handle AFK playtime with units and nickname
        for (String unit : TIME_UNITS) {
            String prefix = "afk_playtime_" + unit.toLowerCase() + "_";
            if (paramLower.startsWith(prefix)) {
                return handleAFKPlayTime(params.substring(prefix.length()), unit);
            }
        }

        if (paramLower.startsWith("afk_playtime_")) {
            return handleAFKPlayTime(params.substring(13));
        }

        // Handle playtime with units and nickname
        for (String unit : TIME_UNITS) {
            String prefix = "playtime_" + unit.toLowerCase() + "_";
            if (paramLower.startsWith(prefix)) {
                return handlePlayTime(params.substring(prefix.length()), unit);
            }
        }

        if (paramLower.startsWith("playtime_")) {
            return handlePlayTime(params.substring(9));
        }

        if (paramLower.startsWith("lastseen_top_")) {
            return handleLastSeenTop(params.substring(13));
        }

        if (paramLower.startsWith("lastseen_")) {
            return handleLastSeen(params.substring(9));
        }

        return null;
    }

    private String handleLPPrefixTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("wrong top position?");

        if (!plugin.isPermissionsManagerConfigured()) {
            return getErrorMessage("luckperms not loaded");
        }

        try {
            return luckPermsManager.getPrefixAsync(user.getUuid()).get();
        } catch (InterruptedException | ExecutionException e) {
            return getErrorMessage("luckperms retrieve unsuccessful");
        }
    }

    private String handleLastSeenElapsedTop(String posStr, String unit) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("wrong top position?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsedTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("wrong top position?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleLastSeenElapsed(String nickname, String unit) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else if (user.getLastSeen() == null) return getErrorMessage("Last seen data missing");

        // Calculate FRESH elapsed time on every call
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsed(String nickname) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else if (user.getLastSeen() == null) return getErrorMessage("Last seen data missing");

        // Calculate FRESH elapsed time on every call
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleNicknameTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("Wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ? user.getNickname() : getErrorMessage("Wrong top position?");
    }

    private String handlePlayTimeTop(String posStr, String unit) {
        if (!isStringInt(posStr)) return getErrorMessage("Wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ?
                String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit)) :
                getErrorMessage("Wrong top position?");
    }

    private String handlePlayTimeTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("Wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ?
                Utils.ticksToFormattedPlaytime(user.getPlaytime(), playtimeFormat) :
                getErrorMessage("Wrong top position?");
    }

    private String handleAFKPlayTime(String nickname, String unit) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else return String.valueOf(Utils.ticksToTimeUnit(user.getAFKPlaytime(), unit));
    }

    private String handleAFKPlayTime(String nickname) {
        DBUser user = getUserFromCache(nickname);
        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else return Utils.ticksToFormattedPlaytime(user.getAFKPlaytime(), playtimeFormat);
    }

    private String handlePlayTime(String nickname, String unit) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else return String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit));
    }

    private String handlePlayTime(String nickname) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else return Utils.ticksToFormattedPlaytime(user.getPlaytime(), playtimeFormat);

    }


    private String handleLastSeenTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("Wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("Wrong top position?");
        if (user.getLastSeen() == null) return getErrorMessage("Last seen data missing");

        return Utils.formatInstant(user.getLastSeen(), plugin.getConfiguration().getString("datetime-format", "MMM dd, yyyy HH:mm:ss"));
    }

    private String handleLastSeen(String nickname) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else if (user.getLastSeen() == null) return getErrorMessage("Last seen data missing");

        return Utils.formatInstant(user.getLastSeen(), plugin.getConfiguration().getString("datetime-format", "MMM dd, yyyy HH:mm:ss"));
    }

    private String handleFirstJoin(String nickname) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else if (user.getFirstJoin() == null) return getErrorMessage("First join data missing");

        return Utils.formatInstant(user.getFirstJoin(), plugin.getConfiguration().getString("datetime-format", "MMM dd, yyyy HH:mm:ss"));

    }

    private String handleJoinStreak(String nickname) {
        DBUser user = getUserFromCache(nickname);

        if (user == DBUser.LOADING) return getErrorMessage("Loading...");
        else if (user == DBUser.NOT_FOUND) return getErrorMessage("Player not found in db");
        else return String.valueOf(user.getAbsoluteJoinStreak());

    }

    private String handleRank(String nickname) {
        try {
            int position = dbUsersManager.getTopPlayers().indexOf(onlineUsersManager.getOnlineUser(nickname));
            return position != -1 ?
                    String.valueOf(position + 1) : plugin.getConfiguration().getString("placeholders.not-in-leaderboard-message", "-");
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String getErrorMessage(String error) {
        return plugin.getConfiguration().getBoolean("placeholders.enable-errors", false) ?
                "Error: " + error : plugin.getConfiguration().getString("placeholders.default-message", "No data");
    }

    private boolean isStringInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
package me.thegabro.playtimemanager.ExternalPluginSupport;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

public class PlayTimePlaceHolders extends PlaceholderExpansion {
    private static final String[] TIME_UNITS = {"s", "m", "h", "d", "y"};

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private LuckPermsManager luckPermsManager = null;
    private DateTimeFormatter formatter;

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

        // Handle rank placeholder
        if (params.equalsIgnoreCase("rank")) {
            try {

                int position = dbUsersManager.getTopPlayers().indexOf(onlineUsersManager.getOnlineUser(player.getName()))  + 1;

                return position != -1 ? String.valueOf(position) : plugin.getConfiguration().getNotInLeaderboardMessage();
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        // Handle first join placeholder
        if (params.equalsIgnoreCase("firstjoin")) {
            try {
                formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
                return String.valueOf(
                        onlineUsersManager.getOnlineUser(player.getName()).getFirstJoin().format(formatter)
                );
            } catch (Exception e) {
                return getErrorMessage("couldn't get join streak");
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

        // Handle basic playtime placeholders
        if (params.equalsIgnoreCase("playtime")) {
            try {
                return Utils.ticksToFormattedPlaytime(
                        onlineUsersManager.getOnlineUser(player.getName()).getPlaytime()
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

        Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsedTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("wrong top position?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleLastSeenElapsed(String nickname, String unit) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        if (user == null) return getErrorMessage("wrong nickname?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsed(String nickname) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        if (user == null) return getErrorMessage("wrong nickname?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleNicknameTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ? user.getNickname() : getErrorMessage("wrong top position?");
    }

    private String handlePlayTimeTop(String posStr, String unit) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ?
                String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit)) :
                getErrorMessage("wrong top position?");
    }

    private String handlePlayTimeTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ?
                Utils.ticksToFormattedPlaytime(user.getPlaytime()) :
                getErrorMessage("wrong top position?");
    }

    private String handlePlayTime(String nickname, String unit) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        return user != null ?
                String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit)) :
                getErrorMessage("wrong nickname?");
    }

    private String handlePlayTime(String nickname) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        return user != null ?
                Utils.ticksToFormattedPlaytime(user.getPlaytime()) :
                getErrorMessage("wrong nickname?");
    }

    private String handleLastSeenTop(String posStr) {
        if (!isStringInt(posStr)) return getErrorMessage("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return getErrorMessage("wrong top position?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
        return user.getLastSeen().format(formatter);
    }

    private String handleLastSeen(String nickname) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        if (user == null) return getErrorMessage("wrong nickname?");
        if (user.getLastSeen() == null) return getErrorMessage("last seen data missing");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
        return user.getLastSeen().format(formatter);
    }

    private String handleJoinStreak(String nickname) {
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        return user != null ?
                String.valueOf(user.getAbsoluteJoinStreak()) :
                getErrorMessage("wrong nickname?");
    }

    private String handleFirstJoin(String nickname){
        DBUser user = dbUsersManager.getUserFromNickname(nickname);
        formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
        return user != null ?
                user.getFirstJoin().format(formatter) :
                getErrorMessage("wrong nickname?");
    }

    private String handleRank(String nickname){
        try {

            int position = dbUsersManager.getTopPlayers().indexOf(onlineUsersManager.getOnlineUser(nickname)) + 1;
            return position != -1 ? String.valueOf(position) : plugin.getConfiguration().getNotInLeaderboardMessage();

        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String getErrorMessage(String error) {
        return plugin.getConfiguration().isPlaceholdersEnableErrors() ? "Error: " + error :  plugin.getConfiguration().getPlaceholdersDefaultMessage();
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
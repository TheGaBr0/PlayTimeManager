package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class PlayerInfoHandler implements PlaceholderHandler {

    private static final String[] UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private final UserResolver resolver;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final PlaceholderUtils utils;

    public PlayerInfoHandler(UserResolver resolver,
                             PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        String p = params.toLowerCase();
        return p.equals("rank")
                || p.equals("firstjoin")
                || p.startsWith("rank_")
                || p.startsWith("firstjoin_")
                || p.startsWith("nickname_top_")
                || p.startsWith("lastseen_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        if (p.equals("rank")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                int position = dbUsersManager.getTopPlayers().indexOf(onlineUser) + 1;
                return position != 0
                        ? String.valueOf(position)
                        : plugin.getConfiguration().getString("placeholders.not-in-leaderboard-message", "-");
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        if (p.equals("firstjoin")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                Instant firstJoin = onlineUser.getFirstJoin();
                if (firstJoin == null) return utils.error("first join data missing");
                return Utils.formatInstant(firstJoin, datetimeFormat());
            } catch (Exception e) {
                return utils.error("couldn't get first join date");
            }
        }

        if (p.startsWith("rank_")) {
            return handleRank(params.substring(5));
        }

        if (p.startsWith("firstjoin_")) {
            return handleFirstJoin(params.substring(10));
        }

        if (p.startsWith("nickname_top_")) {
            return handleNicknameTop(params.substring(13));
        }

        for (String unit : UNITS) {
            String prefix = "lastseen_elapsed_top_" + unit.toLowerCase() + "_";
            if (p.startsWith(prefix)) {
                return handleLastSeenElapsedTop(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("lastseen_elapsed_top_")) {
            return handleLastSeenElapsedTop(params.substring(21));
        }

        for (String unit : UNITS) {
            String prefix = "lastseen_elapsed_" + unit.toLowerCase() + "_";
            if (p.startsWith(prefix)) {
                return handleLastSeenElapsed(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("lastseen_elapsed_")) {
            return handleLastSeenElapsed(params.substring(17));
        }

        if (p.startsWith("lastseen_top_")) {
            return handleLastSeenTop(params.substring(13));
        }

        if (p.startsWith("lastseen_")) {
            return handleLastSeen(params.substring(9));
        }

        return null;
    }

    private String handleRank(String nickname) {
        try {
            List<DBUser> topPlayers = dbUsersManager.getTopPlayers();
            for (int i = 0; i < topPlayers.size(); i++) {
                if (topPlayers.get(i).getNickname().equals(nickname)) {
                    return String.valueOf(i + 1);
                }
            }
            return plugin.getConfiguration().getString("placeholders.not-in-leaderboard-message", "-");
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String handleFirstJoin(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        if (user.getFirstJoin() == null) return utils.error("First join data missing");
        return Utils.formatInstant(user.getFirstJoin(), datetimeFormat());
    }

    private String handleNicknameTop(String posStr) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null ? user.getNickname() : utils.error("wrong top position?");
    }

    private String handleLastSeenElapsedTop(String posStr, String unit) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return utils.error("wrong top position?");
        if (user.getLastSeen() == null) return utils.error("last seen data missing");
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsedTop(String posStr) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return utils.error("wrong top position?");
        if (user.getLastSeen() == null) return utils.error("last seen data missing");
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleLastSeenElapsed(String nickname, String unit) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        if (user.getLastSeen() == null) return utils.error("Last seen data missing");
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return String.valueOf(Utils.ticksToTimeUnit(duration.getSeconds() * 20, unit));
    }

    private String handleLastSeenElapsed(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        if (user.getLastSeen() == null) return utils.error("Last seen data missing");
        Duration duration = Duration.between(user.getLastSeen(), Instant.now());
        return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
    }

    private String handleLastSeenTop(String posStr) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return utils.error("wrong top position?");
        if (user.getLastSeen() == null) return utils.error("last seen data missing");
        return Utils.formatInstant(user.getLastSeen(), datetimeFormat());
    }

    private String handleLastSeen(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        if (user.getLastSeen() == null) return utils.error("Last seen data missing");
        return Utils.formatInstant(user.getLastSeen(), datetimeFormat());
    }

    private String datetimeFormat() {
        return plugin.getConfiguration().getString("datetime-format", "MMM dd, yyyy HH:mm:ss");
    }
}
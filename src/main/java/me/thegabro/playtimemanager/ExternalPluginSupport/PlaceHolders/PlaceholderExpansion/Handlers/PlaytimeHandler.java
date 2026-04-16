package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;

public class PlaytimeHandler implements PlaceholderHandler {

    private static final String[] UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private final UserResolver resolver;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final PlaceholderUtils utils;

    public PlaytimeHandler(UserResolver resolver, PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        String p = params.toLowerCase();
        return p.equals("playtime")
                || p.equals("afk_playtime")
                || p.startsWith("playtime_top_")
                || p.startsWith("afk_playtime_top_")
                || p.startsWith("playtime_")
                || p.startsWith("afk_playtime_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        if (p.equals("playtime")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                return Utils.ticksToFormattedPlaytime(onlineUser.getPlaytime(), format);
            } catch (Exception e) {
                return utils.error("couldn't get playtime");
            }
        }

        if (p.equals("afk_playtime")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                return Utils.ticksToFormattedPlaytime(onlineUser.getAFKPlaytime(), format);
            } catch (Exception e) {
                return utils.error("couldn't get AFK playtime");
            }
        }

        for (String unit : UNITS) {
            if (p.equals("playtime_" + unit)) {
                OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
                if (onlineUser == null) return utils.error("Loading...");
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(onlineUser.getPlaytime(), unit));
                } catch (Exception e) {
                    return utils.error("couldn't get playtime");
                }
            }
        }

        for (String unit : UNITS) {
            if (p.equals("afk_playtime_" + unit)) {
                OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
                if (onlineUser == null) return utils.error("Loading...");
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(onlineUser.getAFKPlaytime(), unit));
                } catch (Exception e) {
                    return utils.error("couldn't get AFK playtime");
                }
            }
        }


        for (String unit : UNITS) {
            String prefix = "playtime_top_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handlePlaytimeTop(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("playtime_top_")) {
            return handlePlaytimeTop(params.substring(13), format);
        }

        for (String unit : UNITS) {
            String prefix = "afk_playtime_top_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handleAfkPlaytimeTop(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("afk_playtime_top_")) {
            return handleAfkPlaytimeTop(params.substring(17), format);
        }


        for (String unit : UNITS) {
            String prefix = "afk_playtime_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handleAfkPlaytime(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("afk_playtime_")) {
            return handleAfkPlaytime(params.substring(13), format);
        }

        for (String unit : UNITS) {
            String prefix = "playtime_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handlePlaytime(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("playtime_")) {
            return handlePlaytime(params.substring(9), format);
        }

        return null;
    }


    private String handlePlaytimeTop(String posStr, String unit) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null
                ? String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit))
                : utils.error("wrong top position?");
    }

    private String handlePlaytimeTop(String posStr, PlaytimeFormat format) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null
                ? Utils.ticksToFormattedPlaytime(user.getPlaytime(), format)
                : utils.error("wrong top position?");
    }

    private String handleAfkPlaytimeTop(String posStr, String unit) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null
                ? String.valueOf(Utils.ticksToTimeUnit(user.getAFKPlaytime(), unit))
                : utils.error("wrong top position?");
    }

    private String handleAfkPlaytimeTop(String posStr, PlaytimeFormat format) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");
        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        return user != null
                ? Utils.ticksToFormattedPlaytime(user.getAFKPlaytime(), format)
                : utils.error("wrong top position?");
    }

    private String handlePlaytime(String nickname, String unit) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(Utils.ticksToTimeUnit(user.getPlaytime(), unit));
    }

    private String handlePlaytime(String nickname, PlaytimeFormat format) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return Utils.ticksToFormattedPlaytime(user.getPlaytime(), format);
    }

    private String handleAfkPlaytime(String nickname, String unit) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(Utils.ticksToTimeUnit(user.getAFKPlaytime(), unit));
    }

    private String handleAfkPlaytime(String nickname, PlaytimeFormat format) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return Utils.ticksToFormattedPlaytime(user.getAFKPlaytime(), format);
    }
}
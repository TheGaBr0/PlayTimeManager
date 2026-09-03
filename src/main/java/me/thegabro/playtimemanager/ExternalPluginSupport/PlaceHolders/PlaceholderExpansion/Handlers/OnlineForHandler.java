package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;

public class OnlineForHandler implements PlaceholderHandler {

    private static final String[] UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private final UserResolver resolver;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final PlaceholderUtils utils;

    public OnlineForHandler(UserResolver resolver, PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        String p = params.toLowerCase();
        return p.equals("onlinefor") || p.startsWith("onlinefor_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        if (p.equals("onlinefor")) {
            DBUser user = onlineUsersManager.getEffectiveUser(player.getName());
            if (user == null) return utils.error("Loading...");
            long sessionTicks = user instanceof OnlineUser onlineUser ? onlineUser.getSessionPlaytime() : 0;
            try {
                return Utils.ticksToFormattedPlaytime(sessionTicks, format);
            } catch (Exception e) {
                return utils.error("couldn't get online time");
            }
        }

        for (String unit : UNITS) {
            if (p.equals("onlinefor_" + unit)) {
                DBUser user = onlineUsersManager.getEffectiveUser(player.getName());
                if (user == null) return utils.error("Loading...");
                long sessionTicks = user instanceof OnlineUser onlineUser ? onlineUser.getSessionPlaytime() : 0;
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(sessionTicks, unit));
                } catch (Exception e) {
                    return utils.error("couldn't get online time");
                }
            }
        }

        for (String unit : UNITS) {
            String prefix = "onlinefor_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handleOnlineFor(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("onlinefor_")) {
            return handleOnlineFor(params.substring(10), format);
        }

        return null;
    }

    private String handleOnlineFor(String nickname, String unit) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        long sessionTicks = user instanceof OnlineUser onlineUser ? onlineUser.getSessionPlaytime() : 0;
        return String.valueOf(Utils.ticksToTimeUnit(sessionTicks, unit));
    }

    private String handleOnlineFor(String nickname, PlaytimeFormat format) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        long sessionTicks = user instanceof OnlineUser onlineUser ? onlineUser.getSessionPlaytime() : 0;
        return Utils.ticksToFormattedPlaytime(sessionTicks, format);
    }
}

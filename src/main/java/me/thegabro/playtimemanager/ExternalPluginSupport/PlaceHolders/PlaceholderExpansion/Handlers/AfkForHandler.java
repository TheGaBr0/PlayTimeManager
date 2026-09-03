package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;

/**
 * Handles PTM_afkfor placeholders: how long a player has been continuously AFK in their
 * current AFK streak. Returns 0 (or its formatted equivalent) if the player isn't currently
 * AFK, or isn't online at all
 */
public class AfkForHandler implements PlaceholderHandler {

    private static final String[] UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private final UserResolver resolver;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final PlaceholderUtils utils;

    public AfkForHandler(UserResolver resolver, PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        String p = params.toLowerCase();
        return p.equals("afkfor") || p.startsWith("afkfor_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        if (p.equals("afkfor")) {
            DBUser user = onlineUsersManager.getEffectiveUser(player.getName());
            if (user == null) return utils.error("Loading...");
            long afkTicks = user instanceof OnlineUser onlineUser ? onlineUser.getCurrentAFKDuration() : 0;
            try {
                return Utils.ticksToFormattedPlaytime(afkTicks, format);
            } catch (Exception e) {
                return utils.error("couldn't get afk time");
            }
        }

        for (String unit : UNITS) {
            if (p.equals("afkfor_" + unit)) {
                DBUser user = onlineUsersManager.getEffectiveUser(player.getName());
                if (user == null) return utils.error("Loading...");
                long afkTicks = user instanceof OnlineUser onlineUser ? onlineUser.getCurrentAFKDuration() : 0;
                try {
                    return String.valueOf(Utils.ticksToTimeUnit(afkTicks, unit));
                } catch (Exception e) {
                    return utils.error("couldn't get afk time");
                }
            }
        }

        for (String unit : UNITS) {
            String prefix = "afkfor_" + unit + "_";
            if (p.startsWith(prefix)) {
                return handleAfkFor(params.substring(prefix.length()), unit);
            }
        }

        if (p.startsWith("afkfor_")) {
            return handleAfkFor(params.substring(7), format);
        }

        return null;
    }

    private String handleAfkFor(String nickname, String unit) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        long afkTicks = user instanceof OnlineUser onlineUser ? onlineUser.getCurrentAFKDuration() : 0;
        return String.valueOf(Utils.ticksToTimeUnit(afkTicks, unit));
    }

    private String handleAfkFor(String nickname, PlaytimeFormat format) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        long afkTicks = user instanceof OnlineUser onlineUser ? onlineUser.getCurrentAFKDuration() : 0;
        return Utils.ticksToFormattedPlaytime(afkTicks, format);
    }
}

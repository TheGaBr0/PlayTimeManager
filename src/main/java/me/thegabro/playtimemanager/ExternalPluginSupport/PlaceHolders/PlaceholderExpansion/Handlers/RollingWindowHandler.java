package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.JournalPlaceholderCache;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.JournalPlaceholderCache.Ranked;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.JournalPlaceholderCache.Stats;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.JournalPlaceholderCache.Top;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.RollingWindow;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * Playtime in a rolling window, from the journal. The window is {@code last<N><unit>} (last24h, last7d...):
 * <pre>
 * playtime_&lt;window&gt;                       own time, formatted
 * playtime_&lt;window&gt;_&lt;unit&gt;                 own time, as a number of units
 * playtime_&lt;window&gt;[_&lt;unit&gt;]_&lt;player&gt;      same, for another player
 * playtime_top_&lt;window&gt;[_&lt;unit&gt;]_&lt;rank&gt;   time of the player at that rank
 * nickname_top_&lt;window&gt;_&lt;rank&gt;            name of the player at that rank
 * </pre>
 * {@code afk_playtime} works like {@code playtime}. Anything that does not contain a window is left
 * to the other handlers.
 */
public class RollingWindowHandler implements PlaceholderHandler {

    private static final String[] UNITS = {"s", "m", "h", "d", "w", "mo", "y", "mc"};

    private enum Metric { PLAYTIME, AFK_PLAYTIME, NICKNAME }

    private record Request(Metric metric, boolean top, RollingWindow window, String unit, String target) {}

    private final UserResolver resolver;
    private final PlaceholderUtils utils;
    private final JournalPlaceholderCache cache;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    public RollingWindowHandler(UserResolver resolver, PlaceholderUtils utils, JournalPlaceholderCache cache) {
        this.resolver = resolver;
        this.utils = utils;
        this.cache = cache;
    }

    @Override
    public boolean canHandle(String params) {
        return parse(params) != null;
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        Request r = parse(params);
        if (r == null) return null;

        if (r.top()) return handleTop(r, format);

        String uuid;
        if (r.target() == null) {
            DBUser self = player == null ? null : onlineUsersManager.getEffectiveUser(player.getName());
            if (self == null) return utils.error("Loading...");
            uuid = self.getUuid();
        } else {
            DBUser user = resolver.resolve(r.target());
            if (user == DBUser.LOADING) return utils.error("Loading...");
            if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
            uuid = user.getUuid();
        }

        Stats stats = cache.user(uuid, r.window().millis());
        if (stats == null) return utils.error("Loading...");
        return format(r.metric() == Metric.AFK_PLAYTIME ? stats.afk() : stats.online(), r.unit(), format);
    }

    private String handleTop(Request r, PlaytimeFormat format) {
        if (r.target() == null || !utils.isInt(r.target())) return utils.error("wrong top position?");
        int rank = Integer.parseInt(r.target());

        Top top = cache.top(r.window().millis());
        if (top == null) return utils.error("Loading...");

        List<Ranked> list = r.metric() == Metric.AFK_PLAYTIME ? top.byAfk() : top.byOnline();
        if (rank < 1 || rank > list.size()) return utils.error("wrong top position?");

        Ranked entry = list.get(rank - 1);
        return switch (r.metric()) {
            case NICKNAME -> entry.nickname();
            case AFK_PLAYTIME -> format(entry.afk(), r.unit(), format);
            case PLAYTIME -> format(entry.online(), r.unit(), format);
        };
    }

    private String format(long millis, String unit, PlaytimeFormat format) {
        long ticks = millis / 50;
        return unit == null
                ? Utils.ticksToFormattedPlaytime(ticks, format)
                : String.valueOf(Utils.ticksToTimeUnit(ticks, unit));
    }

    /** @return the request, or null when the placeholder has no rolling window in it */
    private Request parse(String params) {
        Metric metric;
        String rest;
        boolean top = false;

        if (params.regionMatches(true, 0, "afk_playtime_", 0, 13)) {
            metric = Metric.AFK_PLAYTIME;
            rest = params.substring(13);
        } else if (params.regionMatches(true, 0, "playtime_", 0, 9)) {
            metric = Metric.PLAYTIME;
            rest = params.substring(9);
        } else if (params.regionMatches(true, 0, "nickname_top_", 0, 13)) {
            metric = Metric.NICKNAME;
            rest = params.substring(13);
            top = true;
        } else {
            return null;
        }

        if (metric != Metric.NICKNAME && rest.regionMatches(true, 0, "top_", 0, 4)) {
            top = true;
            rest = rest.substring(4);
        }

        int sep = rest.indexOf('_');
        RollingWindow window = RollingWindow.parse(sep < 0 ? rest : rest.substring(0, sep));
        if (window == null) return null;

        String tail = sep < 0 ? "" : rest.substring(sep + 1);
        String unit = null;
        String target = tail.isEmpty() ? null : tail;

        if (metric != Metric.NICKNAME) {
            String lower = tail.toLowerCase(Locale.ROOT);
            for (String u : UNITS) {
                if (lower.equals(u)) {
                    unit = u;
                    target = null;
                    break;
                }
                if (lower.startsWith(u + "_")) {
                    unit = u;
                    target = tail.substring(u.length() + 1);
                    break;
                }
            }
        }
        return new Request(metric, top, window, unit, target);
    }
}

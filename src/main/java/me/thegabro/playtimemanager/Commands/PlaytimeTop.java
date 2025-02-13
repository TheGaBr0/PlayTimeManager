package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class PlaytimeTop implements TabExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final LuckPermsManager luckPermsManager = LuckPermsManager.getInstance(plugin);
    private final int TOP_MAX = 100;
    private final Pattern pagePattern = Pattern.compile("p\\d+");
    private int page;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.top")) {
            if (args.length > 0) {
                if (pagePattern.matcher(args[0]).matches()) {
                    if (getPages().contains(args[0])) {
                        page = Integer.parseInt(args[0].substring(1));
                    } else {
                        sender.sendMessage("[§6PlayTime§eManager§f]§7 Page " + args[0].substring(1) + " doesn't exist!");
                        return false;
                    }
                } else {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 The argument is not valid! Use p1, p2, etc.");
                    return false;
                }
            } else {
                page = 1;
            }

            int totalUsers = TOP_MAX;
            onlineUsersManager.updateAllOnlineUsersPlaytime();
            List<DBUser> topPlayers = dbUsersManager.getTopPlayers();

            if (!topPlayers.isEmpty()) {
                if (totalUsers > topPlayers.size())
                    totalUsers = topPlayers.size();

                int totalPages = (int) Math.ceil(Float.parseFloat(String.valueOf(totalUsers)) / 10);

                if (page > 0 && page <= totalPages) {
                    int startIndex = (page - 1) * 10;
                    int endIndex = Math.min(page * 10, totalUsers);

                    // Send header message
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Top " + totalUsers + " players - page: " + page);

                    for (int i = startIndex; i < endIndex; i++) {
                        DBUser user = topPlayers.get(i);
                        Component message = Component.empty();

                        // Add rank number
                        message = message.append(Component.text("§7§l#" + (i + 1) + " "));

                        // Add prefix if enabled
                        if (plugin.isPermissionsManagerConfigured() &&
                                luckPermsManager.isLuckPermsUserLoaded(user.getUuid()) &&
                                plugin.getConfiguration().arePrefixesAllowed()) {
                            String prefix = luckPermsManager.getPrefix(user.getUuid());
                            if (prefix != null && !prefix.isEmpty()) {
                                message = message.append(Utils.parseComplexHex(prefix));
                                message = message.append(Component.text(" "));
                            }
                        }

                        // Add username and playtime
                        message = message.append(Component.text("§e" + user.getNickname() + " §7- §d" +
                                Utils.ticksToFormattedPlaytime(user.getPlaytime())));

                        sender.sendMessage(message);
                    }

                    // Add navigation arrows
                    Component navigationMessage = Component.empty();

                    // Previous page arrow
                    if (page > 1) {
                        Component previousArrow = Component.text("§6«")
                                .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page - 1)))
                                .hoverEvent(HoverEvent.showText(Component.text("§7Click to go to previous page")));
                        navigationMessage = navigationMessage.append(previousArrow);
                    } else {
                        navigationMessage = navigationMessage.append(Component.text("§7«"));
                    }

                    // Page indicator
                    navigationMessage = navigationMessage.append(Component.text(" §7Page " + page + "/" + totalPages + " "));

                    // Next page arrow
                    if (page < totalPages) {
                        Component nextArrow = Component.text("§6»")
                                .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page + 1)))
                                .hoverEvent(HoverEvent.showText(Component.text("§7Click to go to next page")));
                        navigationMessage = navigationMessage.append(nextArrow);
                    } else {
                        navigationMessage = navigationMessage.append(Component.text("§7»"));
                    }

                    sender.sendMessage(navigationMessage);

                } else {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid page!");
                }
            } else {
                sender.sendMessage("[§6PlayTime§eManager§f]§7 No players joined!");
            }
        } else {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }

    public List<String> getPages() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.ceil(Float.parseFloat(String.valueOf(TOP_MAX)) / 10); i++) {
            result.add("p" + (i + 1));
        }
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length > 0) {
            List<String> pages = getPages();
            StringUtil.copyPartialMatches(args[0], pages, completions);
            return completions;
        }

        return null;
    }
}
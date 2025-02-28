package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

public class PlaytimeTop implements TabExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private LuckPermsManager luckPermsManager = null;
    private final int TOP_MAX = 100;
    private final Pattern pagePattern = Pattern.compile("p\\d+");
    private int page;

    public PlaytimeTop() {
        if (plugin.isPermissionsManagerConfigured()) {
            try {
                this.luckPermsManager = LuckPermsManager.getInstance(plugin);
            } catch (NoClassDefFoundError e) {
                // LuckPerms is not loaded, leave luckPermsManager as null
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.top")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
            return false;
        }

        // Parse page number
        if (args.length > 0) {
            if (pagePattern.matcher(args[0]).matches()) {
                if (getPages().contains(args[0])) {
                    page = Integer.parseInt(args[0].substring(1));
                } else {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Page " + args[0].substring(1) + " doesn't exist!"));
                    return false;
                }
            } else {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The argument is not valid! Use p1, p2, etc."));
                return false;
            }
        } else {
            page = 1;
        }

        // Process asynchronously
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Wait for update to complete
                onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                // Now get top players
                List<DBUser> topPlayers = dbUsersManager.getTopPlayers();

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (topPlayers.isEmpty()) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " No players joined!"));
                        return;
                    }

                    int totalUsers = Math.min(TOP_MAX, topPlayers.size());
                    int totalPages = (int) Math.ceil(Float.parseFloat(String.valueOf(totalUsers)) / 10);

                    if (page <= 0 || page > totalPages) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid page!"));
                        return;
                    }

                    // Send header message
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Top " + totalUsers + " players - page: " + page));

                    int startIndex = (page - 1) * 10;
                    int endIndex = Math.min(page * 10, totalUsers);

                    // Create an array to store messages in order
                    CompletableFuture<Component>[] messageFutures = new CompletableFuture[endIndex - startIndex];

                    // Get the format from config
                    String format = plugin.getConfiguration().getPlaytimetopLeaderboardFormat();
                    boolean usePrefixes = plugin.getConfiguration().getPlaytimetopLeaderboardFormat().contains("%PREFIX%") && plugin.isPermissionsManagerConfigured();

                    // Process each player in the page range
                    for (int i = startIndex; i < endIndex; i++) {
                        final int rank = i + 1;
                        final int arrayIndex = i - startIndex;
                        DBUser user = topPlayers.get(i);

                        if (usePrefixes) {
                            messageFutures[arrayIndex] = luckPermsManager.getPrefixAsync(user.getUuid())
                                    .thenApply(prefix -> {
                                        String formattedMessage = format
                                                .replace("%POSITION%", String.valueOf(rank))
                                                .replace("%PREFIX%", prefix != null ? prefix : "")
                                                .replace("%PLAYER_NAME%", user.getNickname())
                                                .replace("%PLAYTIME%", Utils.ticksToFormattedPlaytime(user.getPlaytime()));

                                        // Normalize multiple spaces to single space after all replacements
                                        formattedMessage = formattedMessage.replaceAll("\\s+", " ");

                                        return Component.empty().append(Utils.parseColors(formattedMessage));
                                    });
                        } else {
                            String formattedMessage = format
                                    .replace("%POSITION%", String.valueOf(rank))
                                    .replace("%PREFIX%", "")
                                    .replace("%PLAYER_NAME%", user.getNickname())
                                    .replace("%PLAYTIME%", Utils.ticksToFormattedPlaytime(user.getPlaytime()))
                                    .replaceAll("\\s+", " "); // Normalize multiple spaces to single space

                            messageFutures[arrayIndex] = CompletableFuture.completedFuture(
                                    Component.empty().append(Utils.parseColors(formattedMessage))
                            );
                        }
                    }

                    // Wait for all messages to be prepared, then send them in order
                    CompletableFuture.allOf(messageFutures)
                            .thenRun(() -> {
                                // Send all messages in order
                                for (CompletableFuture<Component> future : messageFutures) {
                                    sender.sendMessage(future.join());
                                }

                                // Add navigation arrows
                                Component navigationMessage = Component.empty();

                                // Previous page arrow
                                if (page > 1) {
                                    Component previousArrow = Utils.parseColors("&6«")
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page - 1)))
                                            .hoverEvent(HoverEvent.showText(Utils.parseColors("&7Click to go to previous page")));
                                    navigationMessage = navigationMessage.append(previousArrow);
                                } else {
                                    navigationMessage = navigationMessage.append(Utils.parseColors("&7«"));
                                }

                                // Page indicator
                                navigationMessage = navigationMessage.append(Utils.parseColors(" &7Page " + page + "/" + totalPages + " "));

                                // Next page arrow
                                if (page < totalPages) {
                                    Component nextArrow = Utils.parseColors("&6»")
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page + 1)))
                                            .hoverEvent(HoverEvent.showText(Utils.parseColors("&7Click to go to next page")));
                                    navigationMessage = navigationMessage.append(nextArrow);
                                } else {
                                    navigationMessage = navigationMessage.append(Utils.parseColors("&7»"));
                                }

                                sender.sendMessage(navigationMessage);
                            });
                });
            } catch (InterruptedException | ExecutionException e) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " Error while loading top players: " + e.getMessage()));
                });
                plugin.getLogger().severe("Error in PlaytimeTop command: " + e.getMessage());
            }
        });

        return true;
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
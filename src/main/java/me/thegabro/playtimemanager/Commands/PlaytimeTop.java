package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Translations.CommandsConfiguration;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
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
    private final CommandsConfiguration config;

    public PlaytimeTop() {
        this.config = plugin.getCommandsConfig();
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
            String noPermMessage = config.getConfig().getString("playtimetop.messages.no-permission");
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + noPermMessage));
            return false;
        }

        // Parse page number
        if (args.length > 0) {
            if (pagePattern.matcher(args[0]).matches()) {
                if (getPages().contains(args[0])) {
                    page = Integer.parseInt(args[0].substring(1));
                } else {
                    String pageNotExistsMessage = config.getConfig().getString("playtimetop.messages.page-not-exists")
                            .replace("%PAGE_NUMBER%", args[0].substring(1));
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + pageNotExistsMessage));
                    return false;
                }
            } else {
                String invalidArgMessage = config.getConfig().getString("playtimetop.messages.invalid-argument");
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + invalidArgMessage));
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
                        String noPlayersMessage = config.getConfig().getString("playtimetop.messages.no-players");
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + noPlayersMessage));
                        return;
                    }

                    int totalUsers = Math.min(TOP_MAX, topPlayers.size());
                    int totalPages = (int) Math.ceil(Float.parseFloat(String.valueOf(totalUsers)) / 10);

                    if (page <= 0 || page > totalPages) {
                        String invalidPageMessage = config.getConfig().getString("playtimetop.messages.invalid-page");
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + invalidPageMessage));
                        return;
                    }

                    // Send header message
                    String headerFormat = config.getConfig().getString("playtimetop.header");
                    String header = headerFormat.replace("%PAGE_NUMBER%", String.valueOf(page));
                    sender.sendMessage(Utils.parseColors(header));

                    int startIndex = (page - 1) * 10;
                    int endIndex = Math.min(page * 10, totalUsers);

                    // Create an array to store messages in order
                    CompletableFuture<Component>[] messageFutures = new CompletableFuture[endIndex - startIndex];

                    // Get the format from config
                    String format = config.getConfig().getString("playtimetop.leaderboard-format");
                    boolean usePrefixes = format.contains("%PREFIX%") && plugin.isPermissionsManagerConfigured();

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

                                Component navigationMessage = Component.empty();

                                // Previous page arrow
                                if (page > 1) {
                                    String prevPageText = config.getConfig().getString("playtimetop.footer.previous-page.text-if-page-exists");
                                    String prevPageHoverText = config.getConfig().getString("playtimetop.footer.previous-page.over-text");

                                    Component previousArrow = Utils.parseColors(prevPageText)
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page - 1)))
                                            .hoverEvent(HoverEvent.showText(Utils.parseColors(prevPageHoverText)));
                                    navigationMessage = navigationMessage.append(previousArrow);
                                } else {
                                    String prevPageNotExistsText = config.getConfig().getString("playtimetop.footer.previous-page.text-if-page-not-exists");
                                    navigationMessage = navigationMessage.append(Utils.parseColors(prevPageNotExistsText));
                                }

                                // Middle text
                                String middleTextFormat = config.getConfig().getString("playtimetop.footer.middle-text");
                                String middleText = middleTextFormat
                                        .replace("%PAGE_NUMBER%", String.valueOf(page))
                                        .replace("%TOTAL_PAGES%", String.valueOf(totalPages));
                                navigationMessage = navigationMessage.append(Utils.parseColors(" " + middleText + " "));

                                // Next page arrow
                                if (page < totalPages) {
                                    String nextPageText = config.getConfig().getString("playtimetop.footer.next-page.text-if-page-exists");
                                    String nextPageHoverText = config.getConfig().getString("playtimetop.footer.next-page.over-text");

                                    Component nextArrow = Utils.parseColors(nextPageText)
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (page + 1)))
                                            .hoverEvent(HoverEvent.showText(Utils.parseColors(nextPageHoverText)));
                                    navigationMessage = navigationMessage.append(nextArrow);
                                } else {
                                    String nextPageNotExistsText = config.getConfig().getString("playtimetop.footer.next-page.text-if-page-not-exists");
                                    navigationMessage = navigationMessage.append(Utils.parseColors(nextPageNotExistsText));
                                }

                                sender.sendMessage(navigationMessage);
                            });
                });
            } catch (InterruptedException | ExecutionException e) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    String loadingErrorMessage = config.getConfig().getString("playtimetop.messages.loading-error")
                            .replace("%ERROR%", e.getMessage());
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + loadingErrorMessage));
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
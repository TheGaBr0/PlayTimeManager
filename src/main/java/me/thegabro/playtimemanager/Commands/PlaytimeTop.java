package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Translations.CommandsConfiguration;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

public class PlaytimeTop implements CommandRegistrar{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private LuckPermsManager luckPermsManager = null;
    private final int TOP_MAX = 100;
    private final Pattern pagePattern = Pattern.compile("p\\d+");
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

    public void registerCommands() {
        new CommandAPICommand("playtimetop")
                .withHelp(
                        "Shows the top players by playtime",
                        "Displays a leaderboard of players ranked by their total playtime. " +
                                "The leaderboard shows 10 players per page and supports pagination. " +
                                "Use the page parameter to navigate through different pages (e.g., p1, p2, p3)."
                )
                .withUsage(
                        "/playtimetop",
                        "/playtimetop <page>",
                        "/pttop",
                        "/pttop <page>"
                )
                .withAliases("pttop")
                .withPermission(CommandPermission.fromString("playtime.top"))
                .withOptionalArguments(new StringArgument("page")
                        .replaceSuggestions(ArgumentSuggestions.stringsAsync(info ->
                                CompletableFuture.supplyAsync(() -> getPages().toArray(new String[0]))
                        ))
                )
                .executes((sender, args) -> {
                    String pageString = (String) args.getOptional("page").orElse("p1");

                    // Extract page number from "p1", "p2", etc.
                    int page = 1;
                    if (pageString.startsWith("p")) {
                        try {
                            page = Integer.parseInt(pageString.substring(1));
                        } catch (NumberFormatException e) {
                            // If parsing fails, default to page 1
                            page = 1;
                        }
                    }

                    executePlaytimeTop(sender, page);
                })
                .register();
    }

    private void executePlaytimeTop(CommandSender sender, int page) {
        // Check permission (though CommandAPI should handle this)
        if (!sender.hasPermission("playtime.top")) {
            String noPermMessage = config.getConfig().getString("playtimetop.messages.no-permission");
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + noPermMessage));
            return;
        }

        // Validate page number
        if (page <= 0) {
            String invalidArgMessage = config.getConfig().getString("playtimetop.messages.invalid-argument");
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + invalidArgMessage));
            return;
        }

        final int finalPage = page;

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
                    int totalPages = (int) Math.ceil((double) totalUsers / 10);

                    if (finalPage <= 0 || finalPage > totalPages) {
                        String invalidPageMessage = config.getConfig().getString("playtimetop.messages.invalid-page");
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " " + invalidPageMessage));
                        return;
                    }

                    // Send header message
                    String headerFormat = config.getConfig().getString("playtimetop.header");
                    String header = headerFormat.replace("%PAGE_NUMBER%", String.valueOf(finalPage));
                    sender.sendMessage(Utils.parseColors(header));

                    int startIndex = (finalPage - 1) * 10;
                    int endIndex = Math.min(finalPage * 10, totalUsers);

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
                                if (finalPage > 1) {
                                    String prevPageText = config.getConfig().getString("playtimetop.footer.previous-page.text-if-page-exists");
                                    String prevPageHoverText = config.getConfig().getString("playtimetop.footer.previous-page.over-text");

                                    Component previousArrow = Utils.parseColors(prevPageText)
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (finalPage - 1)))
                                            .hoverEvent(HoverEvent.showText(Utils.parseColors(prevPageHoverText)));
                                    navigationMessage = navigationMessage.append(previousArrow);
                                } else {
                                    String prevPageNotExistsText = config.getConfig().getString("playtimetop.footer.previous-page.text-if-page-not-exists");
                                    navigationMessage = navigationMessage.append(Utils.parseColors(prevPageNotExistsText));
                                }

                                // Middle text
                                String middleTextFormat = config.getConfig().getString("playtimetop.footer.middle-text");
                                String middleText = middleTextFormat
                                        .replace("%PAGE_NUMBER%", String.valueOf(finalPage))
                                        .replace("%TOTAL_PAGES%", String.valueOf(totalPages));
                                navigationMessage = navigationMessage.append(Utils.parseColors(" " + middleText + " "));

                                // Next page arrow
                                if (finalPage < totalPages) {
                                    String nextPageText = config.getConfig().getString("playtimetop.footer.next-page.text-if-page-exists");
                                    String nextPageHoverText = config.getConfig().getString("playtimetop.footer.next-page.over-text");

                                    Component nextArrow = Utils.parseColors(nextPageText)
                                            .clickEvent(ClickEvent.runCommand("/playtimetop p" + (finalPage + 1)))
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
    }

    public List<String> getPages() {
        List<String> result = new ArrayList<>();
        List<DBUser> topPlayers = dbUsersManager.getTopPlayers();
        int totalUsers = Math.min(TOP_MAX, topPlayers.size());
        int totalPages = (int) Math.ceil((double) totalUsers / 10);

        for (int i = 0; i < totalPages; i++) {
            result.add("p" + (i + 1));
        }
        return result;
    }
}
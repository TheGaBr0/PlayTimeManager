package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimRewards implements CommandRegistrar {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private static final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private static final long GUI_OPEN_COOLDOWN = 1000;

    public void registerCommands(){
        new CommandAPICommand("claimrewards")
                .withPermission(CommandPermission.fromString("playtime.claim"))
                .executesConsole((console, args) -> {
                    console.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command."));
                })
                .executesPlayer((player, args) -> {
                    // Check for rapid GUI opening (potential exploit)
                    UUID playerId = player.getUniqueId();
                    long currentTime = System.currentTimeMillis();
                    if (lastGuiOpenTime.containsKey(playerId)) {
                        long lastTime = lastGuiOpenTime.get(playerId);
                        if (currentTime - lastTime < GUI_OPEN_COOLDOWN) {
                            player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cPlease wait before using this command again."));
                            return;
                        }
                    }
                    lastGuiOpenTime.put(playerId, currentTime);

                    // Create a session token for this GUI interaction
                    String sessionToken = UUID.randomUUID().toString();
                    plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);
                    // Open the rewards inventory with session validation
                    RewardsInfoGui rewardsGui = new RewardsInfoGui(player, dbUsersManager.getUserFromNickname(player.getName()), sessionToken);
                    rewardsGui.openInventory();
                }).register();
    }

}
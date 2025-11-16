package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.GUIs.Player.RewardsInfoGui;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimRewards implements CommandExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private static final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private static final long GUI_OPEN_COOLDOWN = 1000;
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    public ClaimRewards() {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure only players can use this command
        if (!sender.hasPermission("playtime.joinstreak.claim")) {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                    GUIsConfiguration.getInstance().getString("rewards-gui.messages.no-permission")));
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // Check for rapid GUI opening (potential exploit)
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastGuiOpenTime.containsKey(playerId)) {
            long lastTime = lastGuiOpenTime.get(playerId);
            if (currentTime - lastTime < GUI_OPEN_COOLDOWN) {
                player.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        GUIsConfiguration.getInstance().getString("rewards-gui.messages.command-spam")));
                return true;
            }
        }
        lastGuiOpenTime.put(playerId, currentTime);

        // Create a session token for this GUI interaction
        String sessionToken = UUID.randomUUID().toString();
        plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);

        dbUsersManager.getUserFromNicknameAsyncWithContext(player.getName(), "claimrewards command", dbUser -> {
            if (dbUser == null) {
                plugin.getLogger().warning("No DB user found for player: " + player.getName());
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                RewardsInfoGui rewardsGui = new RewardsInfoGui(player, dbUser, sessionToken);
                rewardsGui.openInventory();
            });
        });
        return true;
    }

}
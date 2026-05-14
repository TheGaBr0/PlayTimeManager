package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.List;

public class VanishCommandListener implements Listener {

    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final Configuration config = Configuration.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public VanishCommandListener() {}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanishCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("vanish-protection.enabled", false)) return;

        List<String> watchedCommands = config.getStringList("vanish-protection.commands", List.of("/v", "/vanish"));
        if (watchedCommands.isEmpty()) return;

        // Split into base command + optional arguments
        String[] parts = event.getMessage().substring(1).split("\\s+"); // strip leading /
        String base = "/" + parts[0].toLowerCase();

        plugin.getLogger().info(base + " " + Arrays.toString(parts));

        if (watchedCommands.stream().noneMatch(c -> c.equalsIgnoreCase(base))) return;

        // Determine target: explicit player argument or the command sender itself
        OnlineUser target;
        if (parts.length >= 2) {
            String targetName = parts[1];
            target = onlineUsersManager.getOnlineUser(targetName);
            if (target == null) return; // unknown/offline player, ignore
        } else {
            target = onlineUsersManager.getOnlineUser(event.getPlayer().getName());
            if (target == null) return;
        }

        toggleVanish(target);
    }

    private void toggleVanish(OnlineUser target) {
        if (onlineUsersManager.isCurrentlyVanished(target)) {
            onlineUsersManager.removeVanishedPlayer(target);
            dbUsersManager.updateCachedTopPlayers(target);
        } else {
            onlineUsersManager.addVanishedPlayer(target, true);
        }
    }
}

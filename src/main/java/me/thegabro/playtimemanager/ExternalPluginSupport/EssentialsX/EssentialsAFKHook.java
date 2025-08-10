package me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX;

import com.earth2me.essentials.IEssentials;
import net.ess3.api.IUser;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class EssentialsAFKHook implements Listener {

    private static EssentialsAFKHook instance;
    private JavaPlugin plugin;
    private IEssentials essentials;

    private EssentialsAFKHook() {
        // Private constructor for singleton
    }

    public static EssentialsAFKHook getInstance() {
        if (instance == null) {
            instance = new EssentialsAFKHook();
        }
        return instance;
    }

    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        this.essentials = getEssentials();

        if (essentials != null) {
            plugin.getLogger().info("Successfully hooked into EssentialsX AFK system!");
        } else {
            plugin.getLogger().warning("Failed to hook into EssentialsX! Make sure EssentialsX is installed and enabled.");
        }
    }

    /**
     * Get EssentialsX plugin instance
     */
    private IEssentials getEssentials() {
        Plugin essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin != null && essentialsPlugin instanceof IEssentials) {
            return (IEssentials) essentialsPlugin;
        }
        return null;
    }

    /**
     * Listen for AFK status changes from EssentialsX
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkStatusChange(AfkStatusChangeEvent event) {
        if (event.isCancelled()) return;

        IUser user = event.getAffected();
        Player player = user.getBase();

        if (player == null || !player.isOnline()) return;

        boolean isNowAFK = event.getValue();
        String playerName = player.getName();

        if (isNowAFK) {
            // Player is now AFK
            String afkMessage = user.getAfkMessage();
            if (afkMessage != null && !afkMessage.isEmpty()) {
                plugin.getLogger().info(playerName + " is now AFK: " + afkMessage);
            } else {
                plugin.getLogger().info(playerName + " is now AFK");
            }

            // Call custom AFK handler
            onPlayerGoAFK(player, afkMessage);

        } else {
            // Player is no longer AFK
            plugin.getLogger().info(playerName + " is no longer AFK");

            // Call custom return handler
            onPlayerReturnFromAFK(player);
        }
    }

    /**
     * Called when a player goes AFK
     */
    protected void onPlayerGoAFK(Player player, String afkMessage) {
        // Custom logic can be added here
    }

    /**
     * Called when a player returns from AFK
     */
    protected void onPlayerReturnFromAFK(Player player) {
        // Custom logic can be added here
    }
}
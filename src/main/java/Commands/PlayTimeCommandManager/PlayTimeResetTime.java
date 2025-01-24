package Commands.PlayTimeCommandManager;

import Users.DBUser;
import Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PlayTimeResetTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    public PlayTimeResetTime(CommandSender sender, String[] args){
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args) {
        if (args[0].equals("*")) {
            sender.sendMessage("§f[§6PlayTime§eManager§f]§7 Starting reset of all players' data...");

            // Run the reset process async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Get all users first
                List<DBUser> users = plugin.getDbUsersManager().getAllDBUsers();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                }

                for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    if (offlinePlayer.hasPlayedBefore()) {
                        offlinePlayer.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                    }
                }

                // Process users in chunks to avoid overwhelming the server
                final int CHUNK_SIZE = 5;
                for (int i = 0; i < users.size(); i += CHUNK_SIZE) {
                    final int startIndex = i;
                    final int endIndex = Math.min(i + CHUNK_SIZE, users.size());
                    List<DBUser> chunk = users.subList(startIndex, endIndex);

                    // Process chunk on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (DBUser u : chunk) {
                            u.reset();
                        }
                    });

                    // Add a small delay between chunks
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // Switch back to main thread for final operations
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getDbUsersManager().clearCache();
                    sender.sendMessage("§f[§6PlayTime§eManager§f]§7 All players' playtime data and goals have been reset!");
                    plugin.getDbUsersManager().updateTopPlayersFromDB();
                });
            });
            return;
        }

        // Single user reset remains synchronous
        DBUser user = plugin.getDbUsersManager().getUserFromNickname(args[0]);



        if (user instanceof OnlineUser) {
            Player p = Bukkit.getPlayerExact(args[0]);
            p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
            user.reset();
        } else {
            OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);
            if (p.hasPlayedBefore()) {
                p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
            }
            user.reset();
        }

        sender.sendMessage("§f[§6PlayTime§eManager§f]§7 Reset playtime data and goals for player §e" + args[0] + "§7");
        plugin.getDbUsersManager().updateTopPlayersFromDB();

    }
}

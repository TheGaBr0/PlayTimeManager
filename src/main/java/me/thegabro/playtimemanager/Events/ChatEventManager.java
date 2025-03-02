package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class ChatEventManager implements Listener {
    private static ChatEventManager instance;
    private final Map<UUID, ChatInputSession> activeSessions = new HashMap<>();
    private final PlayTimeManager plugin;

    // Private constructor to prevent instantiation
    public ChatEventManager() {
        this.plugin = PlayTimeManager.getInstance();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Singleton accessor method
    public static synchronized ChatEventManager getInstance() {
        if (instance == null) {
            instance = new ChatEventManager();
        }
        return instance;
    }

    public void startChatInput(Player player, BiConsumer<Player, String> callback) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, false));
    }

    public void startCommandInput(Player player, BiConsumer<Player, String> callback) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ChatInputSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }

        if (!session.commandOnly()) {
            return; // Let the chat handler process this if not command-only mode
        }

        event.setCancelled(true);
        String command = event.getMessage();

        if (!command.startsWith("/")) {
            player.sendMessage(Component.text("§cPlease enter a valid command starting with '/' or type 'cancel' to exit"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, command, session));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ChatInputSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.callback().accept(player, "cancel");
                activeSessions.remove(playerId);
            });
            return;
        }

        if (session.commandOnly() && !message.startsWith("/")) {
            player.sendMessage(Component.text("§cPlease enter a valid command starting with '/' or type 'cancel' to exit"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, message, session));
    }

    private void handleInput(Player player, String message, ChatInputSession session) {
        UUID playerId = player.getUniqueId();
        session.callback().accept(player, message);
        activeSessions.remove(playerId);
    }

    private record ChatInputSession(
            BiConsumer<Player, String> callback,
            boolean commandOnly
    ) {}
}
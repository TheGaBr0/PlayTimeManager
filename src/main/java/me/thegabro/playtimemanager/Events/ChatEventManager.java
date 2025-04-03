package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.format.NamedTextColor;
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
        activeSessions.put(playerId, new ChatInputSession(callback, false, false));
    }

    public void startChatInput(Player player, BiConsumer<Player, String> callback, boolean allowNewlines) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, false, allowNewlines));
    }

    public void startCommandInput(Player player, BiConsumer<Player, String> callback) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, true, false));
    }

    public void startCommandInput(Player player, BiConsumer<Player, String> callback, boolean allowNewlines) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, true, allowNewlines));
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

        // Handle special keywords
        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.callback().accept(player, "cancel");
                activeSessions.remove(playerId);
            });
            return;
        }

        if (message.equalsIgnoreCase("confirm")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String finalMessage = session.getMessageBuffer();
                session.callback().accept(player, finalMessage);
                activeSessions.remove(playerId);
            });
            return;
        }

        // Handle newline keyword if enabled
        if (session.allowNewlines()) {
            if (message.equalsIgnoreCase("newline")) {
                // Get the current message buffer or initialize if empty
                String currentBuffer = session.getMessageBuffer();
                session.setMessageBuffer(currentBuffer + "\n");
                player.sendMessage(Component.text("§7Line break added. Continue typing or type §aconfirm§7 to submit.")
                        .append(Component.newline())
                        .append(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                                .color(NamedTextColor.DARK_GRAY)));
                return;
            }

            if (message.equalsIgnoreCase("removeline")) {
                String currentBuffer = session.getMessageBuffer();
                String newBuffer = removeLastLine(currentBuffer);
                session.setMessageBuffer(newBuffer);
                player.sendMessage(Component.text("§7Removed text back to the previous line break. Continue typing or type §aconfirm§7 to submit.")
                        .append(Component.newline())
                        .append(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                                .color(NamedTextColor.DARK_GRAY)));
                return;
            }
        }

        if (session.commandOnly() && !message.startsWith("/")) {
            player.sendMessage(Component.text("§cPlease enter a valid command starting with '/' or type 'cancel' to exit"));
            return;
        }

        // If newlines are enabled, update the buffer
        if (session.allowNewlines()) {
            String currentBuffer = session.getMessageBuffer();

            // New code: Overwrite the last line instead of appending
            if (currentBuffer.isEmpty()) {
                // If buffer is empty, just set the message
                session.setMessageBuffer(message);
            } else {
                // Find the last newline, if any
                int lastNewlineIndex = currentBuffer.lastIndexOf('\n');

                if (lastNewlineIndex == -1) {
                    // No newlines, replace the entire buffer
                    session.setMessageBuffer(message);
                } else {
                    // Keep everything up to and including the last newline, then add the new message
                    String updatedBuffer = currentBuffer.substring(0, lastNewlineIndex + 1) + message;
                    session.setMessageBuffer(updatedBuffer);
                }
            }
            player.sendMessage(Component.text("§aMessage updated. ")
                            .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("§7Sending a new message will §coverwrite§7 the current line."))
                    .append(Component.newline())
                    .append(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                            .color(NamedTextColor.DARK_GRAY)));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, message, session));
        }
    }

    /**
     * Removes everything from the last line break to the end of the string.
     * If there's no line break, removes everything.
     */
    private String removeLastLine(String text) {
        if (text.isEmpty()) {
            return "";
        }

        int lastIndex = text.lastIndexOf('\n');
        if (lastIndex == -1) {
            // No line breaks found, remove everything
            return "";
        } else {
            // Remove everything after the last line break (including all text on that line)
            return text.substring(0, lastIndex + 1); // Keep the \n
        }
    }

    private void handleInput(Player player, String message, ChatInputSession session) {
        UUID playerId = player.getUniqueId();
        session.callback().accept(player, message);
        activeSessions.remove(playerId);
    }

    private static class ChatInputSession {
        private final BiConsumer<Player, String> callback;
        private final boolean commandOnly;
        private final boolean allowNewlines;
        private String messageBuffer;

        public ChatInputSession(BiConsumer<Player, String> callback, boolean commandOnly, boolean allowNewlines) {
            this.callback = callback;
            this.commandOnly = commandOnly;
            this.allowNewlines = allowNewlines;
            this.messageBuffer = "";
        }

        public BiConsumer<Player, String> callback() {
            return callback;
        }

        public boolean commandOnly() {
            return commandOnly;
        }

        public boolean allowNewlines() {
            return allowNewlines;
        }

        public String getMessageBuffer() {
            return messageBuffer;
        }

        public void setMessageBuffer(String messageBuffer) {
            this.messageBuffer = messageBuffer;
        }
    }
}
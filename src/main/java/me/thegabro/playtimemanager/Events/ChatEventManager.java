package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.*;
import java.util.function.BiConsumer;

public class ChatEventManager implements Listener {
    private static ChatEventManager instance;
    private final Map<UUID, ChatInputSession> activeSessions = new HashMap<>();
    private final PlayTimeManager plugin;

    public ChatEventManager() {
        this.plugin = PlayTimeManager.getInstance();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

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


    public void startChatInput(Player player, BiConsumer<Player, String> callback, boolean allowNewlines, String oldMessage) {
        UUID playerId = player.getUniqueId();
        ChatInputSession session = new ChatInputSession(callback, false, allowNewlines);

        // Parse the old message into rows if it exists
        if (oldMessage != null && !oldMessage.isEmpty()) {
            String[] lines = oldMessage.split("/n");
            for (String line : lines) {
                session.addRow(line);
            }
        }

        activeSessions.put(playerId, session);

        // Immediately display the current message (which contains the old message)
        displayCurrentMessage(player, session, "Edit Previous Message");
    }

    public void startCommandInput(Player player, BiConsumer<Player, String> callback) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, new ChatInputSession(callback, true, false));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
            player.sendMessage(Component.text("Please enter a valid command starting with '/' or type 'cancel' to exit")
                    .color(NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, command, session));
    }



    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
                String finalMessage = session.allowNewlines() ? session.getMessageAsString() : "cancel";
                session.callback().accept(player, finalMessage);
                activeSessions.remove(playerId);
            });
            return;
        }

        if (message.equalsIgnoreCase("confirm")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String finalMessage = session.allowNewlines() ? session.getMessageAsString() : "confirm";
                session.callback().accept(player, finalMessage);
                activeSessions.remove(playerId);
            });
            return;
        }

        // Handle row-specific editing commands
        if (session.allowNewlines() && message.startsWith("edit:")) {
            try {
                String[] parts = message.split(":", 3);
                if (parts.length >= 3) {
                    int rowIndex = Integer.parseInt(parts[1])-1;
                    String newContent = parts[2];

                    if (rowIndex >= 0 && rowIndex < session.getRows().size()) {
                        session.updateRow(rowIndex, newContent);
                        displayCurrentMessage(player, session, "Row " + (rowIndex + 1) + " updated");
                    } else {
                        player.sendMessage(Component.text("Invalid row number: " + (rowIndex+1))
                                .color(NamedTextColor.RED));
                    }
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid row format. Use edit:ROW_NUMBER:NEW_TEXT")
                        .color(NamedTextColor.RED));
            }
            return;
        }

        // Handle newline keyword if enabled
        if (session.allowNewlines()) {
            if (message.equalsIgnoreCase("newline")) {
                session.addRow("");
                displayCurrentMessage(player, session, "New line added");
                return;
            }

            if (message.equalsIgnoreCase("removeline")) {
                if (!session.getRows().isEmpty()) {
                    session.removeLastRow();
                    displayCurrentMessage(player, session, "Last line removed");
                } else {
                    player.sendMessage(Component.text("No lines to remove")
                            .color(NamedTextColor.RED));
                }
                return;
            }
        }

        if (session.commandOnly() && !message.startsWith("/")) {
            player.sendMessage(Component.text("Please enter a valid command starting with '/' or type 'cancel' to exit")
                    .color(NamedTextColor.RED));
            return;
        }

        // If newlines are enabled, update the current row
        if (session.allowNewlines()) {
            // If editing a specific row, update that row
            if (session.getCurrentEditingRow() >= 0) {
                session.updateRow(session.getCurrentEditingRow(), message);
                session.setCurrentEditingRow(-1); // Reset editing state
            } else {
                // Otherwise add/update the last row
                if (session.getRows().isEmpty()) {
                    session.addRow(message);
                } else {
                    session.updateRow(session.getRows().size() - 1, message);
                }
            }

            displayCurrentMessage(player, session, "Message updated");
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, message, session));
        }
    }

    private void displayCurrentMessage(Player player, ChatInputSession session, String headerMessage) {
        List<String> rows = session.getRows();

        Component message = Component.empty()
                .append(Component.text(headerMessage)
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .append(Component.newline());

        if (rows.isEmpty()) {
            message = message.append(Component.text("No content yet. Type a message to begin.")
                    .color(NamedTextColor.GRAY));
        } else {
            // Create row display with edit buttons
            for (int i = 0; i < rows.size(); i++) {
                String rowContent = rows.get(i);

                Component rowDisplay = Component.empty()
                        .append(Component.text("[" + (i + 1) + "] ")
                                .color(NamedTextColor.YELLOW))
                        .append(Utils.parseColors(rowContent.isEmpty() ? "<empty>" : rowContent))
                        .append(Component.space())
                        .append(Component.text("[Edit]")
                                .color(NamedTextColor.AQUA)
                                .decorate(TextDecoration.BOLD)
                                .clickEvent(ClickEvent.suggestCommand("edit:"+(i+1)+":"+rowContent))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to edit this line")
                                        .color(NamedTextColor.GRAY))))
                        .append(Component.newline());

                message = message.append(rowDisplay);
            }
        }

        // Add command buttons at the bottom
        message = message.append(Component.newline())
                .append(Component.text("[+Line]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("newline"))
                        .hoverEvent(HoverEvent.showText(Component.text("Add a new line")
                                .color(NamedTextColor.GRAY))))
                .append(Component.space())
                .append(Component.text("[-Line]")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.suggestCommand("removeline"))
                        .hoverEvent(HoverEvent.showText(Component.text("Remove last line")
                                .color(NamedTextColor.GRAY))))
                .append(Component.space())
                .append(Component.text("[Confirm]")
                        .color(NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.suggestCommand("confirm"))
                        .hoverEvent(HoverEvent.showText(Component.text("Submit your message")
                                .color(NamedTextColor.GRAY))))
                .append(Component.space())
                .append(Component.text("[Cancel]")
                        .color(NamedTextColor.DARK_RED)
                        .clickEvent(ClickEvent.suggestCommand("cancel"))
                        .hoverEvent(HoverEvent.showText(Component.text("Cancel input")
                                .color(NamedTextColor.GRAY))))
                .append(Component.newline())
                .append(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                        .color(NamedTextColor.DARK_GRAY));

        player.sendMessage(message);
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
        private final List<String> rows;
        private int currentEditingRow;

        public ChatInputSession(BiConsumer<Player, String> callback, boolean commandOnly, boolean allowNewlines) {
            this.callback = callback;
            this.commandOnly = commandOnly;
            this.allowNewlines = allowNewlines;
            this.rows = new ArrayList<>();
            this.currentEditingRow = -1; // -1 means not currently editing a specific row
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

        public List<String> getRows() {
            return rows;
        }

        public void addRow(String content) {
            rows.add(content);
        }

        public void updateRow(int index, String content) {
            if (index >= 0 && index < rows.size()) {
                rows.set(index, content);
            } else if (index == rows.size()) {
                rows.add(content);
            }
        }

        public void removeLastRow() {
            if (!rows.isEmpty()) {
                rows.remove(rows.size() - 1);
            }
        }

        public String getMessageAsString() {
            return String.join("/n", rows);
        }

        public int getCurrentEditingRow() {
            return currentEditingRow;
        }

        public void setCurrentEditingRow(int row) {
            this.currentEditingRow = row;
        }
    }
}
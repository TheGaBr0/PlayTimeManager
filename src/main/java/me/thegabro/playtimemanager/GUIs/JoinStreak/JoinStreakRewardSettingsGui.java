package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.GUIs.CommandsGui;
import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.GUIs.PermissionsGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JoinStreakRewardSettingsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 45;
    private Inventory inventory;
    private JoinStreakReward reward;
    private AllJoinStreakRewardsGui parentGui;
    private PlayTimeManager plugin;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int REQUIRED_JOINS = 10;
        static final int REWARD_PERMISSIONS = 12;
        static final int REWARD_MESSAGE = 14;
        static final int REWARD_SOUND = 16;
        static final int REWARD_COMMANDS = 31;
        static final int DELETE_REWARD = 33;
        static final int BACK_BUTTON = 44;
    }

    public JoinStreakRewardSettingsGui() {}

    public JoinStreakRewardSettingsGui(JoinStreakReward reward, AllJoinStreakRewardsGui parentGui) {
        this.reward = reward;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text("Reward: " + reward.getId()));
        this.plugin = PlayTimeManager.getInstance();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void initializeItems() {
        inventory.clear();
        initializeBackground();
        initializeButtons();
    }

    private void initializeBackground() {
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!isButtonSlot(i)) {
                inventory.setItem(i, createGuiItem(
                        Material.BLACK_STAINED_GLASS_PANE,
                        Component.text("§f[§6P.T.M.§f]§7")
                ));
            }
        }
    }

    private boolean isButtonSlot(int slot) {
        return slot == Slots.REQUIRED_JOINS ||
                slot == Slots.REWARD_PERMISSIONS ||
                slot == Slots.REWARD_MESSAGE ||
                slot == Slots.REWARD_SOUND ||
                slot == Slots.REWARD_COMMANDS ||
                slot == Slots.DELETE_REWARD ||
                slot == Slots.BACK_BUTTON;
    }

    private void initializeButtons() {
        // Required joins button
        inventory.setItem(Slots.REQUIRED_JOINS, createGuiItem(
                Material.EXPERIENCE_BOTTLE,
                Component.text("§e§lRequired Joins: §6" + (reward.getRequiredJoins() == -1 ? "-" : reward.getRequiredJoins())),
                Component.text("§7Left-click to increase by 1"),
                Component.text("§7Right-click to decrease by 1"),
                Component.text("§7Shift+Left-click to increase by 10"),
                Component.text("§7Shift+Right-click to decrease by 10")
        ));

        // Permissions button
        List<TextComponent> lore = new ArrayList<>();
        lore.add(Component.text("§7Currently §e" + reward.getPermissions().size() + "§7 " +
                (reward.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")));

        lore.add(Component.text("§7Click to change the permissions"));

        if (!PlayTimeManager.getInstance().isPermissionsManagerConfigured()) {
            lore.add(Component.text(""));
            lore.add(Component.text("§4§lWARNING: §cNo permissions plugin detected!"));
            lore.add(Component.text("§cPermissions will not be assigned"));
        }

        inventory.setItem(Slots.REWARD_PERMISSIONS, createGuiItem(
                Material.NAME_TAG,
                Component.text("§e§lPermissions"),
                lore.toArray(new TextComponent[0])
        ));

        // Message button
        inventory.setItem(Slots.REWARD_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Component.text("§e§lReward Message"),
                Component.text(""),
                Component.text("§7Left-click to edit the message"),
                Component.text("§7Right-click to display the message")
        ));

        // Sound button
        inventory.setItem(Slots.REWARD_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Component.text("§e§lReward Sound"),
                Component.text("§7Current: §f" + reward.getRewardSound()),
                Component.text(""),
                Component.text("§7Left-click to edit the sound"),
                Component.text("§7Right click to play the sound.")
        ));

        // Commands button
        inventory.setItem(Slots.REWARD_COMMANDS, createGuiItem(
                Material.COMMAND_BLOCK,
                Component.text("§e§lCommands"),
                Component.text("§7Currently §e" + reward.getCommands().size() + "§7 " +
                        (reward.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                Component.text("§7Click to manage commands")
        ));

        // Delete button
        inventory.setItem(Slots.DELETE_REWARD, createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete Reward"),
                Component.text("§7Click to delete this reward")
        ));

        // Back button
        inventory.setItem(Slots.BACK_BUTTON, createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("§e§lBack")
        ));
    }

    public ItemStack createGuiItem(Material material, @Nullable TextComponent name, @Nullable TextComponent... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        if (lore != null) {
            meta.lore(Arrays.asList(lore));
        }

        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player player) {
        initializeItems();
        player.openInventory(inventory);
    }

    public void onGUIClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        switch (slot) {
            case Slots.REQUIRED_JOINS:
                handleRequiredJoinsClick(player, clickType);
                break;

            case Slots.REWARD_PERMISSIONS:
                player.closeInventory();
                //new PermissionsGui(reward, this).openInventory(player);
                break;

            case Slots.REWARD_MESSAGE:
                if (clickType == ClickType.LEFT) {
                    openMessageEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    // Test the message
                    player.sendMessage(Utils.parseColors(reward.getRewardMessage()));
                }
                break;

            case Slots.REWARD_SOUND:
                if (clickType == ClickType.LEFT) {
                    openSoundEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    playRewardSound(player);
                }
                break;

            case Slots.REWARD_COMMANDS:
                player.closeInventory();
                //new CommandsGui(reward, this).openInventory(player);
                break;

            case Slots.DELETE_REWARD:
                handleDeleteReward(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;
        }
    }

    private void handleRequiredJoinsClick(Player player, ClickType clickType) {
        int currentJoins = reward.getRequiredJoins();

        if (clickType == ClickType.LEFT) {
            // Left click - increase by 1
            reward.setRequiredJoins(currentJoins + 1);
        } else if (clickType == ClickType.RIGHT) {
            // Right click - decrease by 1
            reward.setRequiredJoins(Math.max(1, currentJoins - 1));
        } else if (clickType == ClickType.SHIFT_LEFT) {
            // Shift+Left click - increase by 10
            reward.setRequiredJoins(currentJoins + 10);
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            // Shift+Right click - decrease by 10
            reward.setRequiredJoins(Math.max(1, currentJoins - 10));
        }

        // Refresh GUI
        initializeItems();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
    }

    private void openMessageEditor(Player player) {
        player.closeInventory();

        // Header with reward id
        Component header = Component.text("✎ Message Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        // Instructions with formatting
        Component instructions = Component.text("Enter the new message for this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text("• Supports legacy and hex color codes (e.g. &6 or &#rrggbb)")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("• Available placeholders: %PLAYER_NAME%, %REQUIRED_JOINS%, %REWARD_NAME%")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Component.text(" to exit")
                                .color(NamedTextColor.GRAY)));

        // Combine all components with proper spacing
        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(divider);

        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("cancel")) {
                reward.setRewardMessage(message);
                player.sendMessage(Component.text("Reward message updated successfully!").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Reward message edit cancelled").color(NamedTextColor.RED));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });

        Component preText = Component.text("You can ")
                .color(TextColor.color(170,170,170));  // Gray color

        Component clickableText = Component.text("[click here]")
                .color(TextColor.color(255,170,0))  // Gold color
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(reward.getRewardMessage()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete the current message")));

        fullMessage = Component.empty()
                .append(Component.text("\n"))
                .append(preText)
                .append(clickableText)
                .append(Component.text(" to autocomplete the current message")
                        .color(TextColor.color(170,170,170)));  // Gray color

        player.sendMessage(fullMessage);
    }

    private void openSoundEditor(Player player) {
        player.closeInventory();

        String actualUrl = "https://jd.papermc.io/paper/1.21.4/org/bukkit/Sound.html";

        // Header with reward id
        Component header = Component.text("✎ Sound Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        // Instructions with better spacing and formatting
        Component instructions = Component.text("Enter the new sound for this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text("• Input is not case-sensitive")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Component.text(" to exit")
                                .color(NamedTextColor.GRAY)));

        // Sound list link with icon
        Component linkText = Component.text("» SOUND LIST ")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("«")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true))
                .clickEvent(ClickEvent.openUrl(actualUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open sounds documentation for 1.21.4")
                        .color(NamedTextColor.WHITE)));

        // Link description
        Component linkInfo = Component.text("Documentation for server version 1.21.4")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true);

        // Combine all components with proper spacing
        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(Component.newline())
                .append(linkText)
                .append(Component.newline())
                .append(linkInfo)
                .append(Component.newline())
                .append(divider);

        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, input) -> {
            if (!input.equalsIgnoreCase("cancel")) {
                Sound sound = null;
                try {
                    sound = (Sound) Sound.class.getField(input.toUpperCase()).get(null);
                } catch (NoSuchFieldException | IllegalAccessException ignored) { }

                if (sound != null) {
                    reward.setRewardSound(input.toUpperCase());
                    player.sendMessage(Component.text("Reward sound updated successfully!").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text(input.toUpperCase() + " is not a valid sound").color(NamedTextColor.YELLOW));
                }
            } else {
                player.sendMessage(Component.text("Reward sound edit cancelled").color(NamedTextColor.RED));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });
    }

    private void playRewardSound(Player player) {
        try {
            String soundName = reward.getRewardSound();
            Sound sound = null;

            // Simple direct field access - most efficient when the name matches exactly
            try {
                sound = (Sound) Sound.class.getField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Log the actual error for debugging if verbose is enabled
                if (plugin.getConfiguration().getGoalsCheckVerbose()) {
                    plugin.getLogger().info("Could not find sound directly, attempting fallback: " + e.getMessage());
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning(String.format("Could not find sound '%s' for reward '%s'",
                        soundName, reward.getId()));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for reward '%s': %s",
                    reward.getRewardSound(), reward.getId(), e.getMessage()));
        }
    }

    private void handleDeleteReward(Player player) {
        ItemStack rewardItem = createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete Reward: " + reward.getId())
        );

        ConfirmationGui confirmationGui = new ConfirmationGui(rewardItem, (confirmed) -> {
            if (confirmed) {
                // Run deletion async
                player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &7Deleting reward &e" + reward.getId() + "&7..."));
                Bukkit.getScheduler().runTaskAsynchronously(PlayTimeManager.getInstance(), () -> {
                    reward.kill();

                    // Switch back to main thread for UI updates
                    Bukkit.getScheduler().runTask(PlayTimeManager.getInstance(), () -> {
                        player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aSuccessfully &7deleted reward &e" + reward.getId()));
                        if (parentGui != null) {
                            parentGui.openInventory(player);
                        }
                    });
                });
            } else {
                // No need for async here since we're just handling UI
                openInventory(player);
            }
        });

        player.closeInventory();
        confirmationGui.openInventory(player);
    }

    private void handleBackButton(Player player) {
        if (parentGui != null) {
            player.closeInventory();
            parentGui.openInventory(player);
        }
    }

    private void reopenMainGui(Player player) {
        Bukkit.getScheduler().runTask(
                PlayTimeManager.getPlugin(PlayTimeManager.class),
                () -> openInventory(player)
        );
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof JoinStreakRewardSettingsGui) {
            if (e.getRawSlot() < e.getInventory().getSize()) {
                e.setCancelled(true);
                JoinStreakRewardSettingsGui gui = (JoinStreakRewardSettingsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getClick());
            }
        }
    }
}
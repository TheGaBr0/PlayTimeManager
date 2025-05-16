package me.thegabro.playtimemanager.GUIs.Goals;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GoalSettingsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 45;
    private Inventory inventory;
    private Goal goal;
    private Object previousGui;
    private PlayTimeManager plugin;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();
    private static final class Slots {
        static final int GOAL_REWARDS = 19;
        static final int GOAL_REQUIREMENTS = 21;
        static final int GOAL_MESSAGE = 23;
        static final int GOAL_SOUND = 25;
        static final int UNCOMPLETE_GOAL = 36;
        static final int GOAL_ACTIVATION_STATUS = 40;
        static final int BACK_BUTTON = 44;
    }

    public GoalSettingsGui(){}

    public GoalSettingsGui(Goal goal, Object previousGui) {
        this.goal = goal;
        this.previousGui = previousGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text(goal.getName() + " - Settings"));
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
        return slot == Slots.GOAL_REWARDS ||
                slot == Slots.GOAL_MESSAGE ||
                slot == Slots.GOAL_SOUND ||
                slot == Slots.GOAL_ACTIVATION_STATUS ||
                slot == Slots.BACK_BUTTON;
    }

    private void initializeButtons() {

        // Permissions button
        List<TextComponent> lore = new ArrayList<>();
        lore.add(Component.text("§7Currently §e" + goal.getRewardPermissions().size() + "§7 " +
                (goal.getRewardPermissions().size() != 1 ? "permissions loaded" : "permission loaded")));

        lore.add(Component.text("§7Click to change the permissions"));

        if (!PlayTimeManager.getInstance().isPermissionsManagerConfigured()) {
            lore.add(Component.text(""));
            lore.add(Component.text("§4§lWARNING: §cNo permissions plugin detected!"));
            lore.add(Component.text("§cPermissions will not be assigned"));
        }


        inventory.setItem(Slots.GOAL_REWARDS, createGuiItem(
                Material.CHEST_MINECART,
                Component.text("§e§lRewards"),
                Component.text("§7Currently §e" + goal.getRewardPermissions().size() + "§7 " +
                        (goal.getRewardPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                Component.text("§7Currently §e" + goal.getRewardCommands().size() + "§7 " +
                        (goal.getRewardCommands().size() != 1 ? "commands loaded" : "command loaded")),
                Component.text(""),
                Component.text("§7Click to manage rewards")

        ));

        // Message button
        inventory.setItem(Slots.GOAL_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Component.text("§e§lGoal Message"),
                Component.text(""),
                Component.text("§7Left-click to edit the message"),
                Component.text("§7Right-click to display the message")
        ));

        // Sound button
        inventory.setItem(Slots.GOAL_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Component.text("§e§lGoal Sound"),
                Component.text("§7Current: §f" + goal.getGoalSound()),
                Component.text(""),
                Component.text("§7Left-click to edit the sound"),
                Component.text("§7Right click to play the sound.")
        ));

        // Activation status button
        inventory.setItem(Slots.GOAL_ACTIVATION_STATUS, createGuiItem(
                goal.isActive() ? Material.GREEN_CONCRETE : Material.RED_CONCRETE,
                Component.text(goal.isActive() ? "§a§lGoal Active" : "§c§lGoal Inactive"),
                Component.text("§7Click to " + (goal.isActive() ? "deactivate" : "activate") + " this goal")
        ));

        // Delete button
        inventory.setItem(Slots.GOAL_REQUIREMENTS, createGuiItem(
                Material.PAPER,
                Component.text("§c§lRequirements"),
                Component.text("§7Currently §e" + goal.getRequirements().getPermissions().size() + "§7 " +
                        (goal.getRequirements().getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                Component.text("§7Currently §e" + goal.getRequirements().getPlaceholderConditions().size() + "§7 " +
                        (goal.getRequirements().getPlaceholderConditions().size() != 1 ? "conditions loaded" : "condition loaded")),
                Component.text(""),
                Component.text("§7Click to manage requirements")
        ));

        // Add uncomplete goal button
        inventory.setItem(Slots.UNCOMPLETE_GOAL, createGuiItem(
                Material.PLAYER_HEAD,
                Component.text("§e§lUncomplete Goal for Player"),
                Component.text(""),
                Component.text("§7Click to remove this goal's completion"),
                Component.text("§7from a specific player")
        ));

        // Back button
        inventory.setItem(Slots.BACK_BUTTON, createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("§e§lBack")
        ));
    }

    public ItemStack createGuiItem(Material material, @Nullable Component name, @Nullable Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }

        ArrayList<Component> metalore = new ArrayList<>();
        if (lore != null) {
            // Disable italic for each lore line
            for (Component loreLine : lore) {
                metalore.add(loreLine.decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(metalore);
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

            case Slots.GOAL_MESSAGE:
                if (clickType == ClickType.LEFT) {
                    openMessageEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    // Test the message
                    player.sendMessage(Utils.parseColors(goal.getGoalMessage()));
                }
                break;

            case Slots.GOAL_SOUND:
                if (clickType == ClickType.LEFT) {
                    openSoundEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    playGoalSound(player);
                }
                break;

            case Slots.GOAL_REWARDS:
                player.closeInventory();
                new GoalRewardsGui(goal, this).openInventory(player);
                break;

            case Slots.GOAL_ACTIVATION_STATUS:
                goal.setActivation(!goal.isActive());
                initializeItems();
                break;

            case Slots.GOAL_REQUIREMENTS:
                player.closeInventory();
                new GoalRequirementsGui(goal, this).openInventory(player);
                break;

            case Slots.UNCOMPLETE_GOAL:
                openUncompleteGoalDialog(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;
        }
    }

    private void openMessageEditor(Player player) {
        player.closeInventory();

        // Header with goal name
        Component header = Utils.parseColors("&6&l✎ Message Editor: &e" + goal.getName());

        // Divider for visual separation
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with formatting
        Component instructions = Utils.parseColors(
                "&fEnter the new message for this goal.\n" +
                        "&7• Supports legacy and hex color codes\n" +
                        "&7• Type &c&ocancel&r&7 to exit"
        );

        Component preText = Utils.parseColors("&7You can ");


        Component clickableText = Utils.parseColors("&l&6[click here]")
                .clickEvent(ClickEvent.suggestCommand(goal.getGoalMessage().replace("§","&")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete the current message")));

        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(Component.newline())
                .append(preText)
                .append(clickableText)
                .append(Utils.parseColors("&7 to autocomplete the current message"))
                .append(Component.newline())
                .append(divider);

        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("cancel")) {
                goal.setGoalMessage(message);
                player.sendMessage(Utils.parseColors("&aGoal message updated successfully!"));
            } else {
                player.sendMessage(Utils.parseColors("&cGoal message edit cancelled"));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });

    }

    private void openSoundEditor(Player player) {
        player.closeInventory();

        String actualUrl = "https://jd.papermc.io/paper/1.21.4/org/bukkit/Sound.html";

        // Header with goal name
        Component header = Utils.parseColors("&6&l✎ Sound Editor: &e" + goal.getName());

        // Divider for visual separation
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors(
                "&fEnter the new sound for this goal.\n" +
                        "&7• Input is not case-sensitive\n" +
                        "&7• Type &c&ocancel&r&7 to exit"
        );

        // Sound list link with icon
        Component linkText = Utils.parseColors(
                "&e&l» SOUND LIST «"
        ).clickEvent(ClickEvent.openUrl(actualUrl))
                .hoverEvent(HoverEvent.showText(Utils.parseColors("&fClick to open sounds documentation for 1.21.4")));

        // Link description
        Component linkInfo = Utils.parseColors("&7&oDocumentation for server version 1.21.4");

        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(linkText)
                .append(Component.newline())
                .append(linkInfo)
                .append(Component.newline())
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
                    goal.setGoalSound(input.toUpperCase());
                    player.sendMessage(Utils.parseColors("&aGoal sound updated successfully!"));
                } else {
                    player.sendMessage(Utils.parseColors("&e" + input.toUpperCase() + " is not a valid sound"));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cGoal sound edit cancelled"));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });
    }

    private void playGoalSound(Player player) {
        try {
            String soundName = goal.getGoalSound();
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
                plugin.getLogger().warning(String.format("Could not find sound '%s' for goal '%s'",
                        soundName, goal.getName()));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for goal '%s': %s",
                    goal.getGoalSound(), goal.getName(), e.getMessage()));
        }
    }

    private void handleBackButton(Player player) {
        if (previousGui != null) {
            player.closeInventory();
            ((AllGoalsGui) previousGui).openInventory(player);
        }
    }


    private void openUncompleteGoalDialog(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&c&l✎ Uncomplete Goal: &e" + goal.getName());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors(
                "&fEnter the player name to uncomplete this goal for.\n" +
                        "&7• Player must have completed this goal\n" +
                        "&7• Type &c&ocancel&r&7 to exit"
        );

        // Create full message
        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(Component.newline())
                .append(divider);

        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, input) -> {
            if (!input.equalsIgnoreCase("cancel")) {
                String playerName = input.replace(" ", "");
                DBUser user = dbUsersManager.getUserFromNickname(playerName);

                if (user == null) {
                    player.sendMessage(Utils.parseColors("&cPlayer not found!"));
                } else if (user.hasCompletedGoal(goal.getName())) {
                    user.unmarkGoalAsCompleted(goal.getName());
                    player.sendMessage(Component.text("[§6PlayTime§eManager§f]§7 Successfully uncompleted goal §a" +
                            goal.getName() + "§7 for player §a" + playerName));
                } else {
                    player.sendMessage(Utils.parseColors("&cPlayer hasn't completed that goal!"));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cUncomplete goal operation cancelled"));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });
    }

    private void reopenMainGui(Player player) {
        Bukkit.getScheduler().runTask(
                PlayTimeManager.getPlugin(PlayTimeManager.class),
                () -> openInventory(player)
        );
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getInventory().getHolder() instanceof GoalSettingsGui) {
            if((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);
                GoalSettingsGui gui = (GoalSettingsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getClick());
            }
        }
    }
}
package me.thegabro.playtimemanager.GUIs.Goals;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.Goals.Goal;
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
        static final int COMPLETION_CHECK = 4;
        static final int GOAL_REWARDS = 19;
        static final int GOAL_REQUIREMENTS = 21;
        static final int GOAL_MESSAGE = 23;
        static final int GOAL_SOUND = 25;
        static final int UNCOMPLETE_GOAL = 36;
        static final int GOAL_ACTIVATION_STATUS = 39;
        static final int GOAL_REPEATABLE_STATUS = 40;
        static final int GOAL_OFFLINE_REWARDS = 41;
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
            inventory.setItem(i, createGuiItem(
                    Material.BLACK_STAINED_GLASS_PANE,
                    Component.text("§f[§6P.T.M.§f]§7")
            ));
        }
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
        Map<String, Object> scheduleInfo = goal.getNextSchedule();
        inventory.setItem(Slots.COMPLETION_CHECK, createGuiItem(
                Material.COMPASS,
                Component.text("§e§lCompletion check settings"),
                Component.text("§7The next completion check will occur in §e"+ scheduleInfo.get("timeRemaining") +
                        "§7 on §e"+scheduleInfo.get("nextCheck")),
                Component.text(""),
                Component.text("§7Completion check time is currently set to: §e"+goal.getCompletionCheckInterval()),
                Component.text("§7which means it will occur §e"+scheduleInfo.get("timeCheckToText")),
                Component.text(""),
                Component.text("§7Click to change when the next check occurs")

        ));

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

        inventory.setItem(Slots.GOAL_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Component.text("§e§lGoal Message"),
                Component.text(""),
                Component.text("§7Left-click to edit the message"),
                Component.text("§7Right-click to display the message")
        ));

        inventory.setItem(Slots.GOAL_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Component.text("§e§lGoal Sound"),
                Component.text("§7Current: §f" + goal.getGoalSound()),
                Component.text(""),
                Component.text("§7Left-click to edit the sound"),
                Component.text("§7Right click to play the sound.")
        ));

        inventory.setItem(Slots.GOAL_ACTIVATION_STATUS, createGuiItem(
                goal.isActive() ? Material.GREEN_CONCRETE : Material.RED_CONCRETE,
                Component.text(goal.isActive() ? "§a§lGoal Active" : "§c§lGoal Inactive"),
                Component.text("§7Click to " + (goal.isActive() ? "deactivate" : "activate") + " this goal")
        ));

        inventory.setItem(Slots.GOAL_REPEATABLE_STATUS, createGuiItem(
                goal.isRepeatable() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(goal.isRepeatable() ? "§a§lGoal repeatable" : "§c§lGoal not repeatable"),
                Component.text("§7Click to make this goal " + (goal.isActive() ? "not " : "") + "repeatable")
        ));

        inventory.setItem(Slots.GOAL_OFFLINE_REWARDS, createGuiItem(
                goal.areOfflineRewardsEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(goal.areOfflineRewardsEnabled() ? "§a§lOffline rewards enabled" : "§c§lOffline rewards disabled"),
                Component.text("§7Click to " + (goal.areOfflineRewardsEnabled() ? "§cdeactivate" : "§aactivate") + " §7offline rewards for this goal"),
                Component.text(""),
                Component.text("§eNote: §7Currently, only players who were"),
                Component.text("§7online during the last completion check"),
                Component.text("§7interval are eligible for offline rewards."),
                Component.text("§7If they miss that window, they won't receive it.")
        ));

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

        inventory.setItem(Slots.UNCOMPLETE_GOAL, createGuiItem(
                Material.PLAYER_HEAD,
                Component.text("§e§lUncomplete Goal for Player"),
                Component.text(""),
                Component.text("§7Click to remove this goal's completion"),
                Component.text("§7from a specific player")
        ));

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

            case Slots.COMPLETION_CHECK:
                if (clickType == ClickType.LEFT) {
                    openCompletionCheckEditor(player);
                }
                break;

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

            case Slots.GOAL_REPEATABLE_STATUS:
                goal.setRepeatable(!goal.isRepeatable());
                initializeItems();
                break;

            case Slots.GOAL_OFFLINE_REWARDS:
                goal.setOfflineRewardEnabling(!goal.areOfflineRewardsEnabled());
                initializeItems();
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

    private void openCompletionCheckEditor(Player player) {
        player.closeInventory();

        // Header with goal name
        Component header = Utils.parseColors("&6&l✎ Edit Completion Check Interval for: &e" + goal.getName());

        // Divider for visual separation
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with formatting
        Component instructions = Utils.parseColors(
                "&7Set how often the goal is checked.&r\n" +
                        "&7• Use one of the following formats:\n"+
                        "&e • SECONDS &7(e.g. 900) or a\n"+
                        "&e • CRON expression&7 (e.g. 0 */15 * * * ?).\n"+
                        "&7• Type &c&ocancel&r&7 to exit\n" +
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n"+
                        "&7For more info regarding the issue and workarounds."
        );


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
                boolean success = goal.setCheckTime(message);

                if(success){
                    Map<String, Object> scheduleInfo = goal.getNextSchedule();

                    Component response1 = Utils.parseColors(
                            "&7Completion check interval updated to occur &e"+ scheduleInfo.get("timeCheckToText")
                    );
                    player.sendMessage(response1);
                    if(goal.isActive()){
                        Component response2 = Utils.parseColors(
                                "&7Changes apply after the next check which will occur in &e"+ scheduleInfo.get("timeRemaining") +
                                        "&7 on &e"+scheduleInfo.get("nextCheck")+"&7.\n"+
                                        "&7Use &e/ptreload &7to apply them now."
                        );
                        player.sendMessage(response2);
                    }else{
                        Component response2 = Utils.parseColors(
                                "&7Remember that the goal is currently &cinactive!");
                        player.sendMessage(response2);
                    }
                }else{
                    Component response = Utils.parseColors(
                            "&7Couldn't update completion check interval since\n&e"+
                                    message+" &7is not a valid input."
                    );
                    player.sendMessage(response);

                }

            } else {
                player.sendMessage(Utils.parseColors("&cCompletion check interval update cancelled"));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });

    }
    //TODO: Implement a generic method for message editors
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
                        "&7• Type &c&ocancel&r&7 to exit\n" +
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n"+ 
                        "&7For more info regarding the issue and workarounds."
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

        String actualUrl = "https://jd.papermc.io/paper/1.21.11/org/bukkit/Sound.html";

        // Header with goal name
        Component header = Utils.parseColors("&6&l✎ Sound Editor: &e" + goal.getName());

        // Divider for visual separation
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors(
                "&fEnter the new sound for this goal.\n" +
                        "&7• Input is not case-sensitive\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n"+
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n"+
                        "&7For more info regarding the issue and workarounds."
        );

        // Sound list link with icon
        Component linkText = Utils.parseColors(
                "&e&l» SOUND LIST «"
        ).clickEvent(ClickEvent.openUrl(actualUrl))
                .hoverEvent(HoverEvent.showText(Utils.parseColors("&fClick to open sounds documentation for 1.21.11")));

        // Link description
        Component linkInfo = Utils.parseColors("&7&oDocumentation for server version 1.21.11");

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
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                //fails silently
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
        Component instructions = Utils.parseColors(
                "&fEnter the player name to uncomplete this goal for.\n" +
                        "&7• Player must have completed this goal\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n" +
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n" +
                        "&7For more info regarding the issue and workarounds."
        );

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
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(Utils.parseColors("&cUncomplete goal operation cancelled"));
                reopenMainGui(player);
                return;
            }

            String playerName = input.replace(" ", "");

            // Async user retrieval
            dbUsersManager.getUserFromNicknameAsyncWithContext(playerName, "uncomplete goal for user", user -> {
                if (user == null) {
                    player.sendMessage(Utils.parseColors("&cPlayer not found!"));
                } else if (user.hasCompletedGoal(goal.getName())) {
                    user.unmarkGoalAsCompleted(goal.getName());
                    player.sendMessage(Component.text("[§6PlayTime§eManager§f]§7 Successfully uncompleted goal §a" +
                            goal.getName() + "§7 for player §a" + playerName));
                } else {
                    player.sendMessage(Utils.parseColors("&cPlayer hasn't completed that goal!"));
                }

                // Reopen the GUI after processing
                reopenMainGui(player);
            });
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
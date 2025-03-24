package me.thegabro.playtimemanager.GUIs.JoinStreak;
import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
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
import java.util.List;

public class JoinStreakRewardSettingsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 54;
    private Inventory inventory;
    private JoinStreakReward reward;
    private AllJoinStreakRewardsGui parentGui;
    private PlayTimeManager plugin;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int REQUIRED_JOINS = 10;
        static final int REWARD_PERMISSIONS = 12;
        static final int REWARD_ICON = 14;
        static final int REWARD_SOUND = 16;
        static final int DESCRIPTION = 29;
        static final int REWARDS_DESCRIPTION = 31;
        static final int REWARD_MESSAGE = 33;
        static final int REWARD_COMMANDS = 40;
        static final int DELETE_REWARD = 45;
        static final int BACK_BUTTON = 53;
    }

    public JoinStreakRewardSettingsGui() {}

    public JoinStreakRewardSettingsGui(JoinStreakReward reward, AllJoinStreakRewardsGui parentGui) {
        this.reward = reward;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Utils.parseColors("Reward: " + reward.getId()));
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
                        Utils.parseColors("&f[&6P.T.M.&f]&7")
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
                slot == Slots.BACK_BUTTON ||
                slot == Slots.DESCRIPTION ||
                slot == Slots.REWARDS_DESCRIPTION ||
                slot == Slots.REWARD_ICON;
    }

    private void initializeButtons() {
        // Required joins button
        inventory.setItem(Slots.REQUIRED_JOINS, createGuiItem(
                Material.EXPERIENCE_BOTTLE,
                Utils.parseColors("&e&lRequired Joins: &6" + (reward.getMinRequiredJoins() == -1 ? "-" : reward.getRequiredJoinsDisplay())),
                Utils.parseColors("&7Click to edit the required joins")
        ));

        // Permissions button
        List<Component> lore = new ArrayList<>();
        lore.add(Utils.parseColors("&7Currently &e" + reward.getPermissions().size() + "&7 " +
                (reward.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")));

        lore.add(Utils.parseColors("&7Click to change the permissions"));

        if (!PlayTimeManager.getInstance().isPermissionsManagerConfigured()) {
            lore.add(Utils.parseColors(""));
            lore.add(Utils.parseColors("&4&lWARNING: &cNo permissions plugin detected!"));
            lore.add(Utils.parseColors("&cPermissions will not be assigned"));
        }

        inventory.setItem(Slots.REWARD_PERMISSIONS, createGuiItem(
                Material.NAME_TAG,
                Utils.parseColors("&e&lPermissions"),
                lore.toArray(new Component[0])
        ));

        // Message button
        inventory.setItem(Slots.REWARD_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Utils.parseColors("&e&lReward Message"),
                Utils.parseColors("&7Left-click to edit the message"),
                Utils.parseColors("&7Right-click to display the message")
        ));

        // Sound button
        inventory.setItem(Slots.REWARD_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Utils.parseColors("&e&lReward Sound"),
                Utils.parseColors("&7Current: &f" + reward.getRewardSound()),
                Utils.parseColors(""),
                Utils.parseColors("&7Left-click to edit the sound"),
                Utils.parseColors("&7Right click to play the sound.")
        ));

        // Commands button
        inventory.setItem(Slots.REWARD_COMMANDS, createGuiItem(
                Material.COMMAND_BLOCK,
                Utils.parseColors("&e&lCommands"),
                Utils.parseColors("&7Currently &e" + reward.getCommands().size() + "&7 " +
                        (reward.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                Utils.parseColors("&7Click to manage commands")
        ));

        // Delete button
        inventory.setItem(Slots.DELETE_REWARD, createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete Reward"),
                Utils.parseColors("&7Click to delete this reward")
        ));

        // Back button
        inventory.setItem(Slots.BACK_BUTTON, createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Utils.parseColors("&e&lBack")
        ));

        // Description button
        inventory.setItem(Slots.REWARDS_DESCRIPTION, createGuiItem(
                Material.PAPER,
                Utils.parseColors("&e&lRewards Description"),
                Utils.parseColors("&7Click to edit Rewards Description"),
                Utils.parseColors("&7Right-click to display the description")

        ));

        // Reward Description button
        inventory.setItem(Slots.DESCRIPTION, createGuiItem(
                Material.WRITABLE_BOOK,
                Utils.parseColors("&e&lReward Description"),
                Utils.parseColors("&7Click to edit full description"),
                Utils.parseColors("&7Right-click to display the description")

        ));

        // Item Icon button
        ItemStack iconItem =
                createGuiItem(Material.valueOf(reward.getItemIcon()),
                        Utils.parseColors("&e&lReward Icon"),
                        Utils.parseColors("&7Click to set an icon"));
        inventory.setItem(Slots.REWARD_ICON, iconItem);
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
            case Slots.REQUIRED_JOINS:
                openRequiredJoinsEditor(player);
                break;

            case Slots.REWARD_PERMISSIONS:
                player.closeInventory();
                new JoinStreakPermissionsGui(reward, this).openInventory(player);
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
                new JoinStreakCommandsGui(reward, this).openInventory(player);
                break;

            case Slots.DELETE_REWARD:
                handleDeleteReward(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;

            case Slots.REWARDS_DESCRIPTION:
                if (clickType == ClickType.LEFT) {
                    openRewardsDescriptionEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    // Test the message
                    player.sendMessage(Utils.parseColors(reward.getRewardDescription()));
                }
                break;

            case Slots.DESCRIPTION:
                if (clickType == ClickType.LEFT) {
                    openDescriptionEditor(player);
                } else if (clickType == ClickType.RIGHT) {
                    // Test the message
                    player.sendMessage(Utils.parseColors(reward.getDescription()));
                }
                break;

            case Slots.REWARD_ICON:
                openItemIconSelector(player);
                break;
        }
    }

    private void openRequiredJoinsEditor(Player player) {
        player.closeInventory();

        // Header with reward id
        Component header = Utils.parseColors("✎ Required Joins Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Utils.parseColors("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        // Get current value display
        String currentValue = reward.getRequiredJoinsDisplay();

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors("Enter the required joins for this reward:")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Utils.parseColors("• Current value: ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors(currentValue)
                                .color(NamedTextColor.YELLOW)))
                .append(Component.newline())
                .append(Utils.parseColors("• Enter a single positive number (e.g. \"5\")")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• OR enter an interval with format \"x1-x2\"")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("  This represents a range of joins from x1 to x2")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("  Example: \"1-25\" triggers on ALL joins from 1st to 25th")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Enter ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("-1")
                                .color(NamedTextColor.RED))
                        .append(Utils.parseColors(" to deactivate this reward")
                                .color(NamedTextColor.GRAY)))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
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

        chatEventManager.startChatInput(player, (p, input) -> {
            if (!input.equalsIgnoreCase("cancel")) {
                boolean success = reward.setRequiredJoinsFromString(input);
                if (success) {
                    String newValue = reward.getRequiredJoinsDisplay();
                    if (input.equals("-1")) {
                        player.sendMessage(Utils.parseColors("Reward has been deactivated!").color(NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Utils.parseColors("Required joins updated to " + newValue + "!").color(NamedTextColor.GREEN));
                    }
                    JoinStreaksManager.getInstance().updateJoinRewardsMap(reward);
                    JoinStreaksManager.getInstance().updateEndLoopReward();
                } else {
                    player.sendMessage(Utils.parseColors("Invalid format. Please enter a positive number, -1 to deactivate, or a valid range (e.g., 1-25).").color(NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Utils.parseColors("Required joins edit cancelled").color(NamedTextColor.RED));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });
    }

    private void openRewardsDescriptionEditor(Player player) {
        player.closeInventory();

        // Header with reward id
        Component header = Utils.parseColors("✎ Rewards Description Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Utils.parseColors("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        Component instructions = Utils.parseColors("Enter a description of what the player receives when achieving this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Utils.parseColors("• Describe the specific rewards or benefits")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• This will be returned by the placeholder %REWARD_DETAILS%")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Example: '500 coins, VIP rank for 7 days'")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, true))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
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
                reward.setRewardDescription(message);
                player.sendMessage(Utils.parseColors("Rewards description updated successfully!").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Utils.parseColors("Rewards description edit cancelled").color(NamedTextColor.RED));
            }
            reopenMainGui(player);
        });
    }

    private void openDescriptionEditor(Player player) {
        player.closeInventory();

        // Header with reward id
        Component header = Utils.parseColors("✎ Reward Description Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Utils.parseColors("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        Component instructions = Utils.parseColors("Enter a brief, general description for this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Utils.parseColors("• Keep it short and concise")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Example: 'A 30 days achievement for dedicated players!'")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, true))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
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
                reward.setDescription(message);
                player.sendMessage(Utils.parseColors("Reward description updated successfully!").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Utils.parseColors("Reward description edit cancelled").color(NamedTextColor.RED));
            }
            reopenMainGui(player);
        });
    }

    private void openItemIconSelector(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("Item Icon Selector")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true);

        Component instructions = Utils.parseColors("Hold the item you want to use as the reward icon and type ")
                .color(NamedTextColor.WHITE)
                .append(Utils.parseColors("confirm")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true))
                .append(Utils.parseColors(" in chat.")
                        .color(NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
                                .color(NamedTextColor.GRAY)));

        player.sendMessage(header);
        player.sendMessage(instructions);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (message.equalsIgnoreCase("confirm")) {
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem.getType() != Material.AIR) {
                    // Create a copy of the item to prevent inventory manipulation
                    reward.setItemIcon(heldItem.clone().getType().toString());
                    player.sendMessage(Utils.parseColors("Reward icon updated successfully!").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Utils.parseColors("You must be holding an item to set as the icon!").color(NamedTextColor.RED));
                }
            } else if (!message.equalsIgnoreCase("cancel")) {
                player.sendMessage(Utils.parseColors("Invalid input. Use 'confirm' or 'cancel'.").color(NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Utils.parseColors("Item icon selection cancelled").color(NamedTextColor.RED));
            }
            reopenMainGui(player);
        });
    }

    private void openMessageEditor(Player player) {
        player.closeInventory();

        // Header with reward id
        Component header = Utils.parseColors("✎ Message Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Utils.parseColors("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        // Instructions with formatting
        Component instructions = Utils.parseColors("Enter the new message for this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Utils.parseColors("• Supports legacy and hex color codes (e.g. &6 or &#rrggbb)")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Available placeholders: %PLAYER_NAME%, %REQUIRED_JOINS%, %REWARD_NAME%, %MIN_JOINS%, %MAX_JOINS%")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
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
                player.sendMessage(Utils.parseColors("Reward message updated successfully!").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Utils.parseColors("Reward message edit cancelled").color(NamedTextColor.RED));
            }

            // Reopen the GUI
            reopenMainGui(player);
        });

        Component preText = Utils.parseColors("You can ")
                .color(TextColor.color(170,170,170));  // Gray color

        Component clickableText = Utils.parseColors("[click here]")
                .color(TextColor.color(255,170,0))  // Gold color
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(reward.getRewardMessage()))
                .hoverEvent(HoverEvent.showText(Utils.parseColors("Click to autocomplete the current message")));

        fullMessage = Component.empty()
                .append(Utils.parseColors("\n"))
                .append(preText)
                .append(clickableText)
                .append(Utils.parseColors(" to autocomplete the current message")
                        .color(TextColor.color(170,170,170)));  // Gray color

        player.sendMessage(fullMessage);
    }

    private void openSoundEditor(Player player) {
        player.closeInventory();

        String actualUrl = "https://jd.papermc.io/paper/1.21.4/org/bukkit/Sound.html";

        // Header with reward id
        Component header = Utils.parseColors("✎ Sound Editor: ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("Reward " + reward.getId())
                        .color(NamedTextColor.YELLOW));

        // Divider for visual separation
        Component divider = Utils.parseColors("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                .color(NamedTextColor.DARK_GRAY);

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors("Enter the new sound for this reward.")
                .color(NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Utils.parseColors("• Input is not case-sensitive")
                        .color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Utils.parseColors("• Type ")
                        .color(NamedTextColor.GRAY)
                        .append(Utils.parseColors("cancel")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Utils.parseColors(" to exit")
                                .color(NamedTextColor.GRAY)));

        // Sound list link with icon
        Component linkText = Utils.parseColors("» SOUND LIST ")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .append(Utils.parseColors("«")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true))
                .clickEvent(ClickEvent.openUrl(actualUrl))
                .hoverEvent(HoverEvent.showText(Utils.parseColors("Click to open sounds documentation for 1.21.4")
                        .color(NamedTextColor.WHITE)));

        // Link description
        Component linkInfo = Utils.parseColors("Documentation for server version 1.21.4")
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
                    player.sendMessage(Utils.parseColors("Reward sound updated successfully!").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Utils.parseColors(input.toUpperCase() + " is not a valid sound").color(NamedTextColor.YELLOW));
                }
            } else {
                player.sendMessage(Utils.parseColors("Reward sound edit cancelled").color(NamedTextColor.RED));
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
                Utils.parseColors("&c&lDelete Reward: " + reward.getId())
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
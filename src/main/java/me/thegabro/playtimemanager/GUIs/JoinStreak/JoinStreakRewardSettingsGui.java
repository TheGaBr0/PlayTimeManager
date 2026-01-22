package me.thegabro.playtimemanager.GUIs.JoinStreak;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.GUIs.Misc.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private static final class Slots {
        static final int REQUIRED_JOINS = 10;
        static final int REWARD_PRIZES = 13;
        static final int REWARD_SOUND = 16;
        static final int DESCRIPTION = 29;
        static final int REWARDS_DESCRIPTION = 31;
        static final int REWARD_MESSAGE = 33;
        static final int REWARD_ICON = 40;
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
                slot == Slots.REWARD_PRIZES ||
                slot == Slots.REWARD_MESSAGE ||
                slot == Slots.REWARD_SOUND ||
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
                Component.text(""),
                Utils.parseColors("&7Click to edit the required joins")
        ));

        inventory.setItem(Slots.REWARD_PRIZES, createGuiItem(
                Material.CHEST_MINECART,
                Utils.parseColors("&e&lPrizes"),
                Utils.parseColors("&7Currently &e" + reward.getPermissions().size() + "&7 " +
                        (reward.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                Utils.parseColors("&7Currently &e" + reward.getCommands().size() + "&7 " +
                        (reward.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                Component.text(""),
                Utils.parseColors("&7Click to manage reward's prizes")
        ));

        // Message button
        inventory.setItem(Slots.REWARD_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Utils.parseColors("&e&lReward Achieved Message"),
                Component.text(""),
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

        // Delete button
        inventory.setItem(Slots.DELETE_REWARD, createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete Reward"),
                Component.text(""),
                Utils.parseColors("&7Click to delete this reward")
        ));

        // Back button
        inventory.setItem(Slots.BACK_BUTTON, createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Utils.parseColors("&e&lBack")
        ));

        List<Component> rewardsDescLore = new ArrayList<>();
        rewardsDescLore.add(Utils.parseColors("&7Click to edit rewards description"));

        if (!reward.getRewardDescription().isEmpty()) {
            rewardsDescLore.add(Component.text(""));
            rewardsDescLore.add(Utils.parseColors("&7Current description:"));

            // Split the description by newlines and add each line
            String[] descriptionLines = reward.getRewardDescription().split("/n");
            for (String line : descriptionLines) {
                // Format each line with a bullet point and yellow color
                rewardsDescLore.add(Utils.parseColors("&7"+line));
            }
        }

        inventory.setItem(Slots.REWARDS_DESCRIPTION, createGuiItem(
                Material.PAPER,
                Utils.parseColors("&e&lRewards Description"),
                rewardsDescLore.toArray(new Component[0])
        ));

        List<Component> DescLore = new ArrayList<>();
        DescLore.add(Utils.parseColors("&7Click to edit reward's description"));

        if (!reward.getRewardDescription().isEmpty()) {
            DescLore.add(Component.text(""));
            DescLore.add(Utils.parseColors("&7Current description:"));

            // Split the description by newlines and add each line
            String[] descriptionLines = reward.getDescription().split("/n");
            for (String line : descriptionLines) {
                // Format each line with a bullet point and yellow color
                DescLore.add(Utils.parseColors("&7"+line));
            }
        }

        // Reward Description button
        inventory.setItem(Slots.DESCRIPTION, createGuiItem(
                Material.WRITABLE_BOOK,
                Utils.parseColors("&e&lDescription"),
                DescLore.toArray(new Component[0])
        ));

        // Item Icon button
        ItemStack iconItem =
                createGuiItem(Material.valueOf(reward.getItemIcon()),
                        Utils.parseColors("&e&lReward Icon"),
                        Component.text(""),
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

            case Slots.REWARD_PRIZES:
                player.closeInventory();
                new JoinStreakRewardPrizesGui(reward, this).openInventory(player);
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

            case Slots.DELETE_REWARD:
                handleDeleteReward(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;

            case Slots.REWARDS_DESCRIPTION:
                if (clickType == ClickType.LEFT) {
                    openRewardsDescriptionEditor(player);
                }
                break;

            case Slots.DESCRIPTION:
                if (clickType == ClickType.LEFT) {
                    openDescriptionEditor(player);
                }
                break;

            case Slots.REWARD_ICON:
                openItemIconSelector(player);
                break;
        }
    }

    private void openRequiredJoinsEditor(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&6&l✎ Required Joins Editor: &eReward " + reward.getId());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String currentValue = reward.getRequiredJoinsDisplay();

        Component instructions = Utils.parseColors(
                "&fEnter the required joins for this reward:\n" +
                        "&7• Current value: &e" + currentValue + "\n" +
                        "&7• Enter a single positive number (e.g. \"5\")\n" +
                        "&7• OR enter an interval with format \"x1-x2\"\n" +
                        "&7  This represents a range of joins from x1 to x2\n" +
                        "&7  Example: \"1-25\" triggers on ALL joins from 1st to 25th\n" +
                        "&7• Enter &c-1 &7to deactivate this reward\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n"+
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

        chatEventManager.startChatInput(player, (p, input) -> {
            if (!input.equalsIgnoreCase("cancel")) {
                boolean success = reward.setRequiredJoinsFromString(input);
                if (success) {
                    String newValue = reward.getRequiredJoinsDisplay();
                    if (input.equals("-1")) {
                        player.sendMessage(Utils.parseColors("&aReward has been deactivated!"));
                    } else {
                        player.sendMessage(Utils.parseColors("&aRequired joins updated to " + newValue + "!"));
                    }
                    JoinStreaksManager.getInstance().getRewardRegistry().updateJoinRewardsMap(reward);
                    JoinStreaksManager.getInstance().getRewardRegistry().updateEndLoopReward();
                } else {
                    player.sendMessage(Utils.parseColors("&cInvalid format. Please enter a positive number, -1 to deactivate, or a valid range (e.g., 1-25)."));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cRequired joins edit cancelled"));
            }

            reopenMainGui(player);
        });
    }

    private void openRewardsDescriptionEditor(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&6&l✎ Rewards Description Editor: &eReward " + reward.getId());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a description of what the player receives when achieving this reward.\n" +
                        "&7• Describe the specific rewards or benefits\n" +
                        "&7• This will be returned by the placeholder %REWARD_DETAILS%\n" +
                        "&e&o• Example: '500 coins, VIP rank for 7 days&r'\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n" +
                        "&7• Type &a&oconfirm&r&7 to submit\n" +
                        "&7• Type &e&onewline&r&7 to start a new line\n" +
                        "&7• Type &e&oremoveline&r&7 to remove the last line"
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
                reward.setRewardDescription(message);
                player.sendMessage(Utils.parseColors("&aRewards description updated successfully!"));
            } else {
                player.sendMessage(Utils.parseColors("&cRewards description edit cancelled"));
            }
            reopenMainGui(player);
        }, true, reward.getRewardDescription());
    }

    private void openDescriptionEditor(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&6&l✎ Reward Description Editor: &eReward " + reward.getId());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a brief, general description for this reward.\n" +
                        "&7• Keep it short and concise\n" +
                        "&e&o• Example: 'A 30 days achievement for dedicated players!&r'\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n" +
                        "&7• Type &a&oconfirm&r&7 to submit\n" +
                        "&7• Type &e&onewline&r&7 to start a new line\n" +
                        "&7• Type &e&oremoveline&r&7 to remove the last line"
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
                reward.setDescription(message);
                player.sendMessage(Utils.parseColors("&aReward description updated successfully!"));
            } else {
                player.sendMessage(Utils.parseColors("&cReward description edit cancelled"));
            }
            reopenMainGui(player);
        }, true, reward.getDescription());
    }

    private void openItemIconSelector(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&6&lItem Icon Selector");

        Component instructions = Utils.parseColors(
                "&7Hold the item you want to use as the reward icon and type &a&lconfirm &r&7in chat.\n" +
                        "&7• Custom items with NBT are currently &a&cnot supported\n"+
                        "&7• Type &c&ocancel&r&7 to exit\n"+
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n"+
                        "&7For more info regarding the issue and workarounds."
        );

        player.sendMessage(header);
        player.sendMessage(instructions);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (message.equalsIgnoreCase("confirm")) {
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem.getType() != Material.AIR) {
                    // Create a copy of the item to prevent inventory manipulation
                    reward.setItemIcon(heldItem.clone().getType().toString());
                    player.sendMessage(Utils.parseColors("&aReward icon updated successfully!"));
                } else {
                    player.sendMessage(Utils.parseColors("&cYou must be holding an item to set as the icon!"));
                }
            } else if (!message.equalsIgnoreCase("cancel")) {
                player.sendMessage(Utils.parseColors("&eInvalid input. Use 'confirm' or 'cancel'."));
            } else {
                player.sendMessage(Utils.parseColors("&cItem icon selection cancelled"));
            }
            reopenMainGui(player);
        });
    }


    private void openMessageEditor(Player player) {
        player.closeInventory();

        Component header = Utils.parseColors("&6&l✎ Message Editor: &eReward " + reward.getId());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter the new message for this reward.\n" +
                        "&7• Supports legacy and hex color codes \n" +
                        "&7• Available placeholders: %PLAYER_NAME%, %REQUIRED_JOINS%\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n"+
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
                reward.setRewardMessage(message);
                player.sendMessage(Utils.parseColors("&aReward message updated successfully!"));
            } else {
                player.sendMessage(Utils.parseColors("&cReward message edit cancelled"));
            }

            reopenMainGui(player);
        });

        Component clickableSuggestion = Component.empty()
                .append(Utils.parseColors("\n"))
                .append(Utils.parseColors("&7You can "))
                .append(Utils.parseColors("&6&l[click here]")
                        .clickEvent(ClickEvent.suggestCommand(reward.getRewardMessage()))
                        .hoverEvent(HoverEvent.showText(Utils.parseColors("&eClick to autocomplete the current message"))))
                .append(Utils.parseColors("&7 to autocomplete the current message"));

        player.sendMessage(clickableSuggestion);
    }

    private void openSoundEditor(Player player) {
        player.closeInventory();

        String actualUrl = "https://jd.papermc.io/paper/1.21.11/org/bukkit/Sound.html";

        Component header = Utils.parseColors("&6&l✎ Sound Editor: &eReward " + reward.getId());

        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter the new sound for this reward.\n" +
                        "&7• Input is not case-sensitive\n" +
                        "&7• Type &c&ocancel&r&7 to exit\n"+
                        "&7If chat input &cdoesn't work&7 please take a look at the wiki\n"+
                        "&7For more info regarding the issue and workarounds."
        );

        Component linkText = Utils.parseColors("&e&l» SOUND LIST «")
                .clickEvent(ClickEvent.openUrl(actualUrl))
                .hoverEvent(HoverEvent.showText(Utils.parseColors("&fClick to open sounds documentation for 1.21.11")));

        Component linkInfo = Utils.parseColors("&7&oDocumentation for server version 1.21.11");

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
                    player.sendMessage(Utils.parseColors("&aReward sound updated successfully!"));
                } else {
                    player.sendMessage(Utils.parseColors("&e" + input.toUpperCase() + " is not a valid sound"));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cReward sound edit cancelled"));
            }

            reopenMainGui(player);
        });
    }


    private void playRewardSound(Player player) {
        try {
            String soundName = reward.getRewardSound();
            Sound sound = null;

            try {
                sound = (Sound) Sound.class.getField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {

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
                player.sendMessage(Utils.parseColors(config.getString("prefix") + " &7Deleting reward &e" + reward.getId() + "&7..."));
                Bukkit.getScheduler().runTaskAsynchronously(PlayTimeManager.getInstance(), () -> {
                    reward.kill();

                    // Switch back to main thread for UI updates
                    Bukkit.getScheduler().runTask(PlayTimeManager.getInstance(), () -> {
                        player.sendMessage(Utils.parseColors(config.getString("prefix") + " &aSuccessfully &7deleted reward &e" + reward.getId()));
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
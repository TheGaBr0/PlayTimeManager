package GUIs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Consumer;

public class ConfirmationGui implements InventoryHolder, Listener {
    private Inventory inv;
    private ItemStack itemToRemove;
    private Consumer<Boolean> callback;

    public ConfirmationGui() {}

    public ConfirmationGui(ItemStack itemToRemove, Consumer<Boolean> callback) {
        this.inv = Bukkit.createInventory(this, 27, Component.text("Confirm removal"));
        this.itemToRemove = itemToRemove;
        this.callback = callback;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void initializeItems() {
        // Fill background
        for(int i = 0; i < 27; i++) {
            inv.setItem(i, createGuiItem(
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1),
                    Component.text("§f[§6P.T.M.§f]§7")
            ));
        }

        // Yes/No buttons
        inv.setItem(11, createGuiItem(
                new ItemStack(Material.GREEN_CONCRETE, 1),
                Component.text("§2§lYes")
        ));
        inv.setItem(15, createGuiItem(
                new ItemStack(Material.RED_CONCRETE, 1),
                Component.text("§4§lNo")
        ));

        // Item to remove
        inv.setItem(13, itemToRemove);
    }

    private ItemStack createGuiItem(ItemStack item, @Nullable TextComponent name, @Nullable TextComponent... lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore != null) {
            meta.lore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player p) {
        initializeItems();
        p.openInventory(inv);
    }

    public void onGUIClick(Player whoClicked, int slot) {
        whoClicked.closeInventory();

        if (slot == 11) { // Yes clicked
            callback.accept(true);
        } else if (slot == 15) { // No clicked
            callback.accept(false);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ConfirmationGui) {
            if ((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);
                ConfirmationGui gui = (ConfirmationGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot());
            }
        }
    }
}
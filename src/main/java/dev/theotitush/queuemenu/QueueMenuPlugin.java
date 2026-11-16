package dev.theotitush.queuemenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QueueMenuPlugin extends JavaPlugin implements Listener {
    // Bukkit hotbar slots are zero-based: slot 3 is the fourth slot.
    private static final int HOTBAR_SLOT = 3;
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "queue_menu_item");
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            giveOrRestoreItem(player);
        }

        // Repairs accidental removal/replacement and keeps the item in its assigned slot.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                giveOrRestoreItem(player);
            }
        }, 20L, 20L);

        getLogger().info("QueueMenu enabled. Protected item is assigned to hotbar slot 4.");
    }

    private ItemStack createMenuItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        Component name = Component.empty()
            .append(Component.text("QUEUE", TextColor.fromHexString("#F05B91")))
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(Component.text("MENU", TextColor.fromHexString("#F04455")))
            .decoration(TextDecoration.BOLD, true);

        meta.displayName(name);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isQueueMenu(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
            .has(itemKey, PersistentDataType.BYTE);
    }

    private void giveOrRestoreItem(Player player) {
        // Remove tagged copies everywhere first, then place exactly one in the fixed slot.
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isQueueMenu(contents[i])) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);

        player.getInventory().setItem(HOTBAR_SLOT, createMenuItem());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> giveOrRestoreItem(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this, () -> giveOrRestoreItem(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No special persistence required; the item is recreated on next join.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isQueueMenu(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            giveOrRestoreItem(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Protect the fixed hotbar cell, and prevent moving/clicking the tagged item.
        if (event.getRawSlot() == HOTBAR_SLOT && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
            return;
        }
        if (isQueueMenu(event.getCurrentItem()) || isQueueMenu(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (isQueueMenu(event.getCurrentItem()) || isQueueMenu(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isQueueMenu(event.getOldCursor()) || event.getRawSlots().contains(HOTBAR_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isQueueMenu(event.getMainHandItem()) || isQueueMenu(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isQueueMenu);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isQueueMenu(event.getItem().getItemStack()) && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            giveOrRestoreItem(player);
        }
    }
}

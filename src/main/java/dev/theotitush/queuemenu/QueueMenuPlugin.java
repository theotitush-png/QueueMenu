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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QueueMenuPlugin extends JavaPlugin implements Listener {
    // Bukkit hotbar slots are zero-based: slot 3 is the fourth slot.
    private static final int HOTBAR_SLOT = 3;
    private static final String MENU_TITLE = "Queues › Preset";
    private NamespacedKey itemKey;

    private static final class QueueMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "queue_menu_item");
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            giveOrRestoreItem(player);
        }

        // Only repair the slot if something actually replaced the item.
        // This avoids constantly recreating the item and causing visual flicker.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isQueueMenu(player.getInventory().getItem(HOTBAR_SLOT))) {
                    player.getInventory().setItem(HOTBAR_SLOT, createMenuItem());
                }
            }
        }, 40L, 40L);

        getLogger().info("QueueMenu enabled. Protected totem is assigned to hotbar slot 4.");
    }

    private ItemStack createMenuItem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();

        Component name = Component.empty()
            .append(Component.text("QUEUE", TextColor.fromHexString("#F05B91")))
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(Component.text("MENU", TextColor.fromHexString("#F04455")))
            .decoration(TextDecoration.BOLD, true);

        meta.displayName(name);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isQueueMenu(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
            .has(itemKey, PersistentDataType.BYTE);
    }

    private void giveOrRestoreItem(Player player) {
        // Remove any duplicate tagged copies, but leave the correct item untouched.
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != HOTBAR_SLOT && isQueueMenu(contents[i])) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);

        if (!isQueueMenu(player.getInventory().getItem(HOTBAR_SLOT))) {
            player.getInventory().setItem(HOTBAR_SLOT, createMenuItem());
        }
    }

    private Component sampledGradient(String text, String[] colors) {
        return sampledGradient(text, colors, true);
    }

    private Component sampledGradient(String text, String[] colors, boolean bold) {
        Component result = Component.empty();
        int colorIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            String character = String.valueOf(text.charAt(i));
            if (character.equals(" ")) {
                result = result.append(Component.text(" "));
                continue;
            }
            String hex = colors[Math.min(colorIndex, colors.length - 1)];
            result = result.append(Component.text(character, TextColor.fromHexString(hex))
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false));
            colorIndex++;
        }
        return result;
    }

    private ItemStack namedItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean featuredQueue = name.equalsIgnoreCase("Sword")
            || name.equalsIgnoreCase("Axe")
            || name.equalsIgnoreCase("Mace")
            || name.equalsIgnoreCase("Spear")
            || name.equalsIgnoreCase("Netherite")
            || name.equalsIgnoreCase("Diamond Pot")
            || name.equalsIgnoreCase("Diamond SMP");

        if (featuredQueue) {
            Component title = switch (name.toUpperCase(java.util.Locale.ROOT)) {
                // Per-character colours sampled from the supplied SWORD tooltip.
                case "SWORD" -> sampledGradient("SWORD", new String[] {
                    "#A8D4FF", "#9FEFFF", "#9CFCF9", "#9FF4F6", "#A8D4FF"
                });
                // Per-character colours sampled from the supplied AXE tooltip.
                case "AXE" -> sampledGradient("AXE", new String[] {
                    "#73FFEC", "#73FFEC", "#73FFEC"
                });
                // Per-character colours sampled from the supplied SPEAR MACE tooltip.
                case "MACE" -> sampledGradient("MACE", new String[] {
                    "#DAE7E8", "#D5EBED", "#D7EDF0", "#DFEEF0"
                });
                case "SPEAR" -> sampledGradient("SPEAR MACE", new String[] {
                    "#9153D9", "#A84CD8", "#BE46D8", "#D53FD7", "#EB38D6",
                    "#EB38D6", "#EB38D6", "#EB38D6", "#EB38D6"
                });
                // Per-character colours sampled from the supplied DIAMOND SMP tooltip.
                case "DIAMOND SMP" -> sampledGradient("DIAMOND SMP", new String[] {
                    "#85B8FF", "#8CBCFF", "#92C0FF", "#99C3FF", "#9FC7FF",
                    "#A6CBFF", "#9FC7FF", "#92C0FF", "#8CBCFF", "#85B8FF"
                });
                // Per-character colours sampled from the supplied DIAMOND POT tooltip.
                case "DIAMOND POT" -> sampledGradient("DIAMOND POT", new String[] {
                    "#4FAAFF", "#5AAFFF", "#64B4FF", "#6FB9FF", "#7ABEFF",
                    "#85C3FF", "#85C3FF", "#6FB9FF", "#64B4FF", "#5AAFFF"
                });
                // Per-character colours sampled from the supplied NETHERITE POT tooltip.
                case "NETHERITE" -> sampledGradient("NETHERITE POT", new String[] {
                    "#D6512F", "#D85631", "#D95B34", "#DB6136", "#DD6638",
                    "#DE6B3B", "#E0703D", "#DE6B3B", "#DD6638", "#D95B34",
                    "#D85631", "#D6512F"
                });
                default -> Component.text(name.toUpperCase(java.util.Locale.ROOT), NamedTextColor.WHITE);
            };

            Component action = Component.empty()
                .append(Component.text("▰", TextColor.fromHexString("#08FB54")))
                .append(Component.text("▏ LMB TO ", TextColor.fromHexString("#A8A8A8")))
                .append(Component.text("JOIN", TextColor.fromHexString("#08FB54")))
                .decoration(TextDecoration.ITALIC, false);
            meta.displayName(title);
            meta.lore(java.util.List.of(action));
        } else {
            meta.displayName(Component.text(name, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                Component.text(lore, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private Inventory createQueueInventory() {
        QueueMenuHolder holder = new QueueMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
            Component.text(MENU_TITLE, NamedTextColor.GRAY));
        holder.setInventory(inventory);

        ItemStack filler = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        filler.setItemMeta(fillerMeta);

        // Top strip and centre sword match the supplied queue-menu screenshot.
        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) inventory.setItem(slot, filler);
        }
        inventory.setItem(4, namedItem(Material.GOLDEN_SWORD, "Preset Queues", "Choose a preset queue"));

        // Preset queue icons, arranged like the two icon rows in the reference.
        Material[] materials = {
            Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.MACE, Material.NETHERITE_SPEAR,
            Material.NETHERITE_SWORD, Material.COOKED_BEEF, Material.DIAMOND,
            Material.DIAMOND_CHESTPLATE,
            Material.SHIELD, Material.TOTEM_OF_UNDYING, Material.GOLDEN_APPLE,
            Material.NETHER_STAR, Material.EMERALD, Material.ENDER_PEARL,
            Material.DIAMOND
        };
        String[] names = {
            "Sword", "Axe", "Mace", "Spear", "Netherite", "Diamond Pot", "Diamond SMP", "Armour",
            "Shield", "Totem", "Gapple", "Special", "Economy", "Pearl", "Diamond"
        };
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37};
        for (int i = 0; i < materials.length; i++) {
            inventory.setItem(slots[i], namedItem(materials[i], names[i], "Preset queue"));
        }

        return inventory;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> giveOrRestoreItem(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this, () -> giveOrRestoreItem(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        // Handle both right-clicking air and right-clicking a block with the
        // totem in the main hand. Do not depend on another plugin leaving the
        // interaction uncancelled.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!isQueueMenu(heldItem)) return;

        event.setCancelled(true);
        player.openInventory(createQueueInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isQueueMenu(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Keep the custom menu read-only: clicks cannot take or move its icons.
        if (event.getView().getTopInventory().getHolder() instanceof QueueMenuHolder) {
            event.setCancelled(true);
            return;
        }

        // Protect slot 4, number-key swaps into it, and any click involving the tagged item.
        if ((event.getClickedInventory() == player.getInventory()
                && event.getSlot() == HOTBAR_SLOT)
            || event.getHotbarButton() == HOTBAR_SLOT
            || isQueueMenu(event.getCurrentItem())
            || isQueueMenu(event.getCursor())) {
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
        if (isQueueMenu(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize && rawSlot - topSize == HOTBAR_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getView().getTopInventory().getHolder() instanceof QueueMenuHolder) {
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
        if (isQueueMenu(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }
}

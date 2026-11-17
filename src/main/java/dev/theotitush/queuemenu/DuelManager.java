package dev.theotitush.queuemenu;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/** Per-mode 1v1 queues, kits and duels for the preset queue menu. */
final class DuelManager implements Listener, TabExecutor {
    private static final TextColor GREEN = TextColor.fromHexString("#08FB54");
    private static final TextColor GREY = TextColor.fromHexString("#A8A8A8");
    private static final long COUNTDOWN_MS = 3000L;
    private static final long RETURN_DELAY_TICKS = 60L;

    private final QueueMenuPlugin plugin;
    private final Map<String, Deque<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> queuedMode = new HashMap<>();
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Map<String, Supplier<ItemStack[]>> kits = new HashMap<>();

    private static final class Saved {
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack offhand;
        Location location;
        GameMode gameMode;
        double health;
        int food;
        float saturation;
        int level;
        float exp;
    }

    private static final class Duel {
        final UUID a;
        final UUID b;
        final String mode;
        final long liveAt;
        boolean over;
        final Map<UUID, Saved> saved = new HashMap<>();

        Duel(UUID a, UUID b, String mode) {
            this.a = a;
            this.b = b;
            this.mode = mode;
            this.liveAt = System.currentTimeMillis() + COUNTDOWN_MS;
        }

        UUID opponent(UUID id) {
            return id.equals(a) ? b : a;
        }
    }

    DuelManager(QueueMenuPlugin plugin) {
        this.plugin = plugin;
        registerKits();
    }

    // ---------- public API used by the menu ----------

    boolean isInDuel(Player player) {
        return duels.containsKey(player.getUniqueId());
    }

    void join(Player player, String modeName) {
        String mode = modeName.toLowerCase(Locale.ROOT);
        if (!kits.containsKey(mode)) return;
        UUID id = player.getUniqueId();

        if (duels.containsKey(id)) {
            msg(player, "You are already in a duel.", NamedTextColor.RED);
            return;
        }

        String current = queuedMode.get(id);
        if (mode.equals(current)) {
            leave(player, true);
            return;
        }
        if (current != null) leave(player, false);

        if (arena(1) == null || arena(2) == null) {
            msg(player, "No arena is set up yet. An admin must run /queueadmin setspawn 1 and 2.",
                NamedTextColor.RED);
            return;
        }

        Deque<UUID> queue = queues.computeIfAbsent(mode, k -> new ArrayDeque<>());
        Player opponent = null;
        for (Iterator<UUID> it = queue.iterator(); it.hasNext();) {
            Player candidate = Bukkit.getPlayer(it.next());
            it.remove();
            if (candidate != null && candidate.isOnline() && !candidate.equals(player)) {
                queuedMode.remove(candidate.getUniqueId());
                opponent = candidate;
                break;
            }
        }

        if (opponent == null) {
            queue.add(id);
            queuedMode.put(id, mode);
            player.sendMessage(Component.text("Joined the ", GREY)
                .append(Component.text(modeName, GREEN))
                .append(Component.text(" queue. Use ", GREY))
                .append(Component.text("/leavequeue", GREEN))
                .append(Component.text(" to leave.", GREY)));
            return;
        }
        startDuel(opponent, player, mode, modeName);
    }

    void leave(Player player, boolean notify) {
        UUID id = player.getUniqueId();
        String mode = queuedMode.remove(id);
        if (mode != null) {
            Deque<UUID> queue = queues.get(mode);
            if (queue != null) queue.remove(id);
            if (notify) msg(player, "You left the queue.", GREY);
        } else if (notify) {
            msg(player, "You are not in a queue.", GREY);
        }
    }

    void shutdown() {
        for (Duel duel : new java.util.HashSet<>(duels.values())) {
            for (UUID id : new UUID[] {duel.a, duel.b}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) restore(p, duel);
            }
        }
        duels.clear();
        queues.clear();
        queuedMode.clear();
    }

    // ---------- duel flow ----------

    private Location arena(int n) {
        return plugin.getConfig().getLocation("arena.spawn" + n);
    }

    private void startDuel(Player first, Player second, String mode, String modeName) {
        Duel duel = new Duel(first.getUniqueId(), second.getUniqueId(), mode);
        duels.put(duel.a, duel);
        duels.put(duel.b, duel);

        Player[] players = {first, second};
        for (int i = 0; i < 2; i++) {
            Player p = players[i];
            duel.saved.put(p.getUniqueId(), save(p));
            prepare(p, mode);
            p.teleport(arena(i + 1));
            p.sendMessage(Component.text("Match found! ", GREEN)
                .append(Component.text(modeName + " vs " + players[1 - i].getName(), GREY))
                .append(Component.text(" - fight starts in 3 seconds.", GREY)));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duel.over) return;
            for (Player p : players) {
                if (p.isOnline()) msg(p, "Fight!", GREEN);
            }
        }, COUNTDOWN_MS / 50L);
    }

    private void endDuel(Duel duel, UUID loserId) {
        if (duel.over) return;
        duel.over = true;
        UUID winnerId = duel.opponent(loserId);
        Player loser = Bukkit.getPlayer(loserId);
        Player winner = Bukkit.getPlayer(winnerId);

        String loserName = loser != null ? loser.getName() : "Opponent";
        String winnerName = winner != null ? winner.getName() : "Opponent";
        Component result = Component.text(winnerName, GREEN)
            .append(Component.text(" won the duel against ", GREY))
            .append(Component.text(loserName, GREEN))
            .append(Component.text(".", GREY));
        if (winner != null) winner.sendMessage(result);
        if (loser != null) loser.sendMessage(result);

        duels.remove(duel.a);
        duels.remove(duel.b);

        if (loser != null) restore(loser, duel);
        if (winner != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (winner.isOnline() && !duels.containsKey(winnerId)) restore(winner, duel);
            }, RETURN_DELAY_TICKS);
        }
    }

    private Saved save(Player p) {
        PlayerInventory inv = p.getInventory();
        Saved s = new Saved();
        s.contents = cloneAll(inv.getStorageContents());
        s.armor = cloneAll(inv.getArmorContents());
        s.offhand = inv.getItemInOffHand().clone();
        s.location = p.getLocation().clone();
        s.gameMode = p.getGameMode();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.level = p.getLevel();
        s.exp = p.getExp();
        return s;
    }

    private void restore(Player p, Duel duel) {
        Saved s = duel.saved.get(p.getUniqueId());
        if (s == null) return;
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(s.contents);
        inv.setArmorContents(s.armor);
        inv.setItemInOffHand(s.offhand);
        clearState(p);
        p.setGameMode(s.gameMode);
        p.setHealth(Math.min(s.health, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        p.setFoodLevel(s.food);
        p.setSaturation(s.saturation);
        p.setLevel(s.level);
        p.setExp(s.exp);
        p.teleport(s.location);
        plugin.giveOrRestoreItem(p);
        duel.saved.remove(p.getUniqueId());
    }

    private void prepare(Player p, String mode) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        clearState(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);

        if (applySavedKit(p, mode)) return;

        ItemStack[] kit = kits.get(mode).get();
        // kit layout: [0..3] boots, leggings, chestplate, helmet; [4] offhand; [5..] hotbar/inventory items
        inv.setArmorContents(new ItemStack[] {kit[0], kit[1], kit[2], kit[3]});
        inv.setItemInOffHand(kit[4]);
        for (int i = 5; i < kit.length; i++) {
            if (kit[i] != null) inv.addItem(kit[i]);
        }
    }

    // ---------- admin-saved kits (/setkit) ----------

    private void saveKit(Player p, String mode) {
        PlayerInventory inv = p.getInventory();
        String base = "kits." + mode.replace(' ', '_');
        plugin.getConfig().set(base + ".contents", Arrays.asList(stripMenu(inv.getStorageContents())));
        plugin.getConfig().set(base + ".armor", Arrays.asList(stripMenu(inv.getArmorContents())));
        plugin.getConfig().set(base + ".offhand", plugin.isQueueMenu(inv.getItemInOffHand())
            ? null : inv.getItemInOffHand().clone());
        plugin.saveConfig();
    }

    /** Copies the inventory, leaving out the protected queue-menu totem. */
    private ItemStack[] stripMenu(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            copy[i] = (it == null || plugin.isQueueMenu(it)) ? null : it.clone();
        }
        return copy;
    }

    private boolean applySavedKit(Player p, String mode) {
        String base = "kits." + mode.replace(' ', '_');
        if (!plugin.getConfig().isList(base + ".contents")) return false;
        PlayerInventory inv = p.getInventory();
        inv.setStorageContents(readItems(base + ".contents", 36));
        inv.setArmorContents(readItems(base + ".armor", 4));
        ItemStack off = plugin.getConfig().getItemStack(base + ".offhand");
        inv.setItemInOffHand(off == null ? new ItemStack(Material.AIR) : off);
        return true;
    }

    private ItemStack[] readItems(String path, int size) {
        List<?> raw = plugin.getConfig().getList(path, new ArrayList<>());
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size && i < raw.size(); i++) {
            if (raw.get(i) instanceof ItemStack stack) items[i] = stack;
        }
        return items;
    }

    private void clearState(Player p) {
        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }

    // ---------- listeners ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Duel duel = duels.get(player.getUniqueId());
        if (duel == null || duel.over) return;

        if (System.currentTimeMillis() < duel.liveAt) {
            event.setCancelled(true);
            return;
        }
        if (player.getHealth() - event.getFinalDamage() <= 0) {
            // End the duel instead of letting the player die so nothing drops.
            event.setCancelled(true);
            endDuel(duel, player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;

        Duel vd = duels.get(victim.getUniqueId());
        Duel ad = duels.get(attacker.getUniqueId());
        if (vd == null && ad == null) return;
        // Only the two opponents can hurt each other while a duel is running.
        if (vd == null || vd != ad) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        leave(p, false);
        Duel duel = duels.get(p.getUniqueId());
        if (duel != null) endDuel(duel, p.getUniqueId());
    }

    // ---------- commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("leavequeue")) {
            if (sender instanceof Player p) leave(p, true);
            return true;
        }
        if (name.equals("setkit")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Run this in-game.");
                return true;
            }
            if (!p.hasPermission("queuemenu.admin")) {
                msg(p, "You don't have permission to do that.", NamedTextColor.RED);
                return true;
            }
            String mode = String.join(" ", args).toLowerCase(Locale.ROOT).trim();
            if (mode.startsWith("gamemode ")) mode = mode.substring(9).trim();
            if (!kits.containsKey(mode)) {
                msg(p, "Usage: /setkit <gamemode>  (" + String.join(", ", kits.keySet()) + ")", GREY);
                return true;
            }
            saveKit(p, mode);
            p.sendMessage(Component.text("Saved your inventory, armour and offhand as the ", GREY)
                .append(Component.text(mode, GREEN))
                .append(Component.text(" kit.", GREY)));
            return true;
        }
        if (name.equals("queueadmin")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Run this in-game.");
                return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")
                    && (args[1].equals("1") || args[1].equals("2"))) {
                plugin.getConfig().set("arena.spawn" + args[1], p.getLocation());
                plugin.saveConfig();
                msg(p, "Arena spawn " + args[1] + " set to your location.", GREEN);
            } else {
                msg(p, "Usage: /queueadmin setspawn <1|2>", GREY);
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("setkit") || !sender.hasPermission("queuemenu.admin")) {
            return List.of();
        }
        String typed = String.join(" ", args).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String mode : kits.keySet()) {
            if (mode.startsWith(typed)) out.add(mode);
        }
        return out;
    }

    private void msg(Player p, String text, TextColor color) {
        p.sendMessage(Component.text(text, color));
    }

    // ---------- kits ----------

    private static ItemStack item(Material m) {
        return new ItemStack(m);
    }

    private static ItemStack item(Material m, int amount) {
        return new ItemStack(m, amount);
    }

    private static ItemStack ench(ItemStack stack, Enchantment e, int level) {
        stack.addUnsafeEnchantment(e, level);
        return stack;
    }

    private static ItemStack potion(Material type, PotionType potion, int amount) {
        ItemStack stack = new ItemStack(type, amount);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(potion);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack[] kit(Material boots, Material legs, Material chest, Material helm,
            int protection, ItemStack offhand, ItemStack... items) {
        ItemStack[] kit = new ItemStack[5 + items.length];
        Material[] armor = {boots, legs, chest, helm};
        for (int i = 0; i < 4; i++) {
            kit[i] = ench(item(armor[i]), Enchantment.PROTECTION, protection);
            kit[i].addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        }
        kit[4] = offhand;
        System.arraycopy(items, 0, kit, 5, items.length);
        return kit;
    }

    private void registerKits() {
        kits.put("sword", () -> kit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, 3, item(Material.SHIELD),
            ench(item(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 3),
            item(Material.GOLDEN_APPLE, 8)));

        kits.put("axe", () -> kit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, 3, item(Material.SHIELD),
            ench(item(Material.DIAMOND_AXE), Enchantment.SHARPNESS, 3),
            item(Material.GOLDEN_APPLE, 8)));

        kits.put("mace", () -> kit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, 4, item(Material.SHIELD),
            ench(item(Material.MACE), Enchantment.DENSITY, 3),
            item(Material.WIND_CHARGE, 32), item(Material.GOLDEN_APPLE, 8)));

        kits.put("spear", () -> kit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, 4, item(Material.SHIELD),
            item(Material.NETHERITE_SPEAR), item(Material.GOLDEN_APPLE, 8)));

        kits.put("netherite", () -> kit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, 4, null,
            ench(item(Material.NETHERITE_SWORD), Enchantment.SHARPNESS, 5),
            item(Material.GOLDEN_APPLE, 16), item(Material.ENDER_PEARL, 8),
            potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 16)));

        kits.put("diamond pot", () -> kit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, 4, null,
            ench(item(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 5),
            item(Material.GOLDEN_APPLE, 8), item(Material.ENDER_PEARL, 8),
            potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 20),
            potion(Material.POTION, PotionType.STRONG_SWIFTNESS, 2)));

        kits.put("diamond smp", () -> kit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, 3, item(Material.SHIELD),
            ench(item(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 3),
            item(Material.GOLDEN_APPLE, 8), item(Material.ENDER_PEARL, 4),
            item(Material.COOKED_BEEF, 16)));

        kits.put("smp", () -> kit(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
            Material.IRON_CHESTPLATE, Material.IRON_HELMET, 2, item(Material.SHIELD),
            item(Material.IRON_SWORD), item(Material.BOW), item(Material.ARROW, 32),
            item(Material.COOKED_BEEF, 16)));
    }
}

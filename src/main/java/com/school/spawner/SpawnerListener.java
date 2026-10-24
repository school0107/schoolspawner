package com.school.spawner;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class SpawnerListener implements Listener {
    private final SchoolSpawner plugin;
    private final SpawnerManager manager;

    public SpawnerListener(SchoolSpawner plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    private void msg(Player p, String key, String... repl) {
        String m = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        for (int i = 0; i + 1 < repl.length; i += 2) {
            m = m.replace(repl[i], repl[i + 1]);
        }
        p.sendMessage(TextUtil.color(prefix + m));
    }

    // 1. Kill mob rơi lồng
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        // Bỏ qua nếu là mob triệu hồi từ lồng
        if (entity.hasMetadata("schoolspawner_mob")) return;

        double chance = plugin.getConfig().getDouble("settings.kill-drop-chance", 1.0);
        if (Math.random() <= chance) {
            EntityType type = entity.getType();
            ItemStack spawnerItem = manager.createSpawnerItem(type, 1, killer.getUniqueId(), killer.getName());
            entity.getWorld().dropItemNaturally(entity.getLocation(), spawnerItem);
        }
    }

    // 2. Đặt lồng
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) return;
        ItemStack item = e.getItemInHand();
        EntityType type = manager.getMobFromItem(item);
        if (type == null) return;

        Player p = e.getPlayer();
        int level = manager.getLevelFromItem(item);
        UUID owner = manager.getOwnerFromItem(item);
        String ownerName = manager.getOwnerNameFromItem(item);
        if (owner == null) { owner = p.getUniqueId(); ownerName = p.getName(); }

        SpawnerData data = new SpawnerData(e.getBlock().getLocation(), type, Math.max(1, level), owner, ownerName);
        manager.put(data);

        msg(p, "spawner-placed", "%mob%", SpawnerManager.pretty(type), "%level%", String.valueOf(level));
    }

    // 3. Đập lồng
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData data = manager.getAt(b.getLocation());
        if (data == null) return;

        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(data.owner) && !p.hasPermission("schoolspawner.admin")) {
            e.setCancelled(true);
            msg(p, "not-owner");
            return;
        }

        e.setDropItems(false);
        ItemStack drop = manager.createSpawnerItem(data.entityType, data.level, data.owner, data.ownerName);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        manager.remove(b.getLocation());
        msg(p, "spawner-broken", "%mob%", SpawnerManager.pretty(data.entityType), "%level%", String.valueOf(data.level));
    }

    // 4. Shift + Chuột phải để gộp toàn bộ trên tay
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;

        Player p = e.getPlayer();
        SpawnerData data = manager.getAt(b.getLocation());
        if (data == null) return;

        e.setCancelled(true);
        boolean isOwner = p.getUniqueId().equals(data.owner);
        boolean isAdmin = p.hasPermission("schoolspawner.admin");

        ItemStack inHand = p.getInventory().getItemInMainHand();
        EntityType handMob = manager.getMobFromItem(inHand);

        // Gộp lồng (Shift + Chuột phải)
        if (p.isSneaking() && handMob != null && plugin.getConfig().getBoolean("settings.allow-merge", true)) {
            if (handMob != data.entityType) {
                msg(p, "merge-fail-different");
                return;
            }
            if (!isOwner && !isAdmin) {
                msg(p, "not-owner");
                return;
            }

            int maxLevel = plugin.getConfig().getInt("settings.max-level", 100);
            int handAmount = inHand.getAmount();
            int handLevel = manager.getLevelFromItem(inHand);
            int totalLevelToAdd = handAmount * handLevel;

            if (data.level + totalLevelToAdd > maxLevel) {
                msg(p, "merge-fail-max");
                return;
            }

            // Xoá toàn bộ lồng trên tay
            p.getInventory().setItemInMainHand(null);

            data.level += totalLevelToAdd;
            data.lastGenerate = System.currentTimeMillis();
            manager.put(data);

            msg(p, "spawner-merged", "%mob%", SpawnerManager.pretty(data.entityType), "%level%", String.valueOf(data.level));
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            return;
        }

        // Mở GUI
        if (plugin.getConfig().getBoolean("settings.owner-only-gui", true) && !isOwner && !isAdmin) {
            msg(p, "not-owner");
            return;
        }
        plugin.getGui().open(p, data);
    }

    // 5. Click trong GUI
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title());
        if (!title.contains("Lồng")) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        
        NamespacedKey locKey = plugin.getGui().getLocKey();
        String keyStr = meta.getPersistentDataContainer().get(locKey, PersistentDataType.STRING);
        if (keyStr == null) return;

        SpawnerData data = null;
        for (SpawnerData d : manager.all()) {
            if (SpawnerManager.locKey(d.location).equals(keyStr)) { data = d; break; }
        }
        if (data == null) { p.closeInventory(); return; }

        if (clicked.getType() == Material.GOLD_INGOT) {
            double money = data.storedMoney;
            if (money <= 0) {
                msg(p, "no-money");
                return;
            }
            
            if (!giveMoney(p, money)) {
                msg(p, "no-economy");
                return;
            }

            data.storedMoney = 0;
            manager.put(data);
            
            msg(p, "money-received", "%money%", SpawnerGUI.formatMoney(money), "%mob%", SpawnerManager.pretty(data.entityType));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGui().open(p, data));
        }
    }

    private boolean giveMoney(Player p, double amount) {
        try {
            Class<?> vault = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(vault);
            if (registration != null) {
                Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                vault.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                        .invoke(provider, p, amount);
                return true;
            }
        } catch (Throwable ignored) {}
        
        String cmd = plugin.getConfig().getString("settings.fallback-money-command", "");
        if (cmd != null && !cmd.isEmpty()) {
            String c = cmd.replace("%player%", p.getName()).replace("%amount%", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            return true;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + p.getName() + " " + (long) amount);
            return true;
        }
        return false;
    }
}
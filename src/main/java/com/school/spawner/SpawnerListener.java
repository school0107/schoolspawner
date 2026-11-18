package com.school.spawner;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
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
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpawnerListener implements Listener {
    private final SchoolSpawner plugin;
    private final SpawnerManager manager;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

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

    // Sự kiện sử dụng item (Bùa + Vật phẩm triệu hồi Giờ Vàng)
    @EventHandler
    public void onCharmUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        NamespacedKey key = new NamespacedKey(plugin, "lucky_charm");
        String charmType = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (charmType == null) return;

        e.setCancelled(true);
        
        if (charmType.equals("perm")) {
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            
            double bonus = plugin.getConfig().getDouble("lucky-charm.drop-bonus", 0.05);
            manager.addPlayerDropBonus(p.getUniqueId(), bonus);
            
            double total = manager.getPlayerDropBonus(p.getUniqueId()) * 100;
            p.sendMessage(TextUtil.color("&#55FF55✔ Đã dùng Bùa Vĩnh Viễn! Tổng tỷ lệ rơi lồng: &#FFAA00" + String.format("%.0f", total) + "%"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        } 
        else if (charmType.equals("temp")) {
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            
            double bonus = plugin.getConfig().getDouble("temp-lucky-charm.drop-bonus", 0.10);
            long durationSec = plugin.getConfig().getLong("temp-lucky-charm.duration-seconds", 1800);
            long durationMillis = durationSec * 1000;
            
            manager.addTempPlayerDropBonus(p.getUniqueId(), bonus, durationMillis);
            
            p.sendMessage(TextUtil.color("&#55FF55✔ Đã dùng Bùa Thời Hạn! Tỷ lệ rơi lồng tăng thêm &#FFAA00" + (bonus * 100) + "% &#55FF55trong &#FFAA00" + (durationSec / 60) + " phút."));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        }
        else if (charmType.equals("capacity")) {
            double bonus = plugin.getConfig().getDouble("capacity-charm.bonus-capacity", 10000000);
            double maxCap = plugin.getConfig().getDouble("capacity-charm.max-capacity-bonus", 500000000);
            
            double currentBonus = manager.getPlayerCapacityBonus(p.getUniqueId());
            
            if (currentBonus >= maxCap) {
                p.sendMessage(TextUtil.color("&#FF5555✖ Bạn đã đạt giới hạn dung lượng tối đa (" + SpawnerGUI.formatMoney(maxCap) + "$)!"));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            
            manager.addPlayerCapacityBonus(p.getUniqueId(), bonus);
            
            double total = manager.getPlayerCapacityBonus(p.getUniqueId());
            p.sendMessage(TextUtil.color("&#55FF55✔ Đã dùng Bùa Dung Lượng! Tổng giới hạn tăng thêm: &#00E5FF" + SpawnerGUI.formatMoney(total) + "$"));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
        }
        // Vật phẩm triệu hồi Giờ Vàng
        else if (charmType.equals("goldenhour_item")) {
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            
            manager.startGoldenHour(2.0, 10 * 60 * 1000, p.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerSpawn(SpawnerSpawnEvent e) {
        if (manager.getAt(e.getSpawner().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        if (entity.hasMetadata("schoolspawner_mob")) return;

        List<String> blacklist = plugin.getConfig().getStringList("mob-blacklist");
        if (blacklist.contains(entity.getType().name())) return;

        double chance = plugin.getConfig().getDouble("settings.kill-drop-chance", 0.5);
        if (killer.hasPermission("spawnerdrop.4")) chance = 1.0;
        else if (killer.hasPermission("spawnerdrop.3")) chance = 0.9;
        else if (killer.hasPermission("spawnerdrop.2")) chance = 0.7;
        else if (killer.hasPermission("spawnerdrop.1")) chance = 0.5;

        chance += manager.getPlayerDropBonus(killer.getUniqueId());
        if (chance > 1.0) chance = 1.0;

        if (Math.random() <= chance) {
            EntityType type = entity.getType();
            ItemStack spawnerItem = manager.createSpawnerItem(type, 1, killer.getUniqueId(), killer.getName());
            entity.getWorld().dropItemNaturally(entity.getLocation(), spawnerItem);
            msg(killer, "drop-spawner", "%mob%", SpawnerManager.pretty(type));
        }
    }

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

        Block b = e.getBlock();
        
        BlockState state = b.getState();
        if (state instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            spawner.update(true, false);
        }

        SpawnerData data = new SpawnerData(b.getLocation(), type, Math.max(1, level), owner, ownerName);
        manager.put(data);

        msg(p, "spawner-placed", "%mob%", SpawnerManager.pretty(type), "%level%", String.valueOf(level));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData data = manager.getAt(b.getLocation());
        
        if (data != null) {
            Player p = e.getPlayer();
            e.setExpToDrop(0);
            e.setDropItems(false);

            ItemStack drop = manager.createSpawnerItem(data.entityType, data.level, data.owner, data.ownerName);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            manager.remove(b.getLocation());
            msg(p, "spawner-broken", "%mob%", SpawnerManager.pretty(data.entityType), "%level%", String.valueOf(data.level));
            return;
        }
        
        BlockState state = b.getState();
        if (state instanceof CreatureSpawner spawner) {
            EntityType type = spawner.getSpawnedType();
            if (type != null && type.isAlive() && type.isSpawnable()) {
                e.setExpToDrop(0);
                e.setDropItems(false);
                ItemStack drop = manager.createSpawnerItem(type, 1, e.getPlayer().getUniqueId(), e.getPlayer().getName());
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }
    }

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

        ItemStack inHand = p.getInventory().getItemInMainHand();
        EntityType handMob = manager.getMobFromItem(inHand);

        if (p.isSneaking() && handMob != null && plugin.getConfig().getBoolean("settings.allow-merge", true)) {
            if (handMob != data.entityType) {
                msg(p, "merge-fail-different");
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

            p.getInventory().setItemInMainHand(null);
            data.level += totalLevelToAdd;
            data.lastGenerate = System.currentTimeMillis();
            manager.put(data);

            msg(p, "spawner-merged", "%mob%", SpawnerManager.pretty(data.entityType), "%level%", String.valueOf(data.level));
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            return;
        }

        plugin.getGui().open(p, data);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title());
        if (!title.contains("Lồng")) return;
        e.setCancelled(true);

        long now = System.currentTimeMillis();
        if (clickCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
        clickCooldown.put(p.getUniqueId(), now + 500);

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

        final SpawnerData finalData = data;

        Material moneyMat = Material.matchMaterial(plugin.getConfig().getString("gui.money-item", "GOLD_INGOT"));
        if (moneyMat == null) moneyMat = Material.GOLD_INGOT;

        if (clicked.getType() == moneyMat) {
            double baseMoney = finalData.storedMoney;
            if (baseMoney <= 0) {
                msg(p, "no-money");
                return;
            }

            double multiplier = manager.getPermissionMultiplier(p);
            double finalMoney = baseMoney * multiplier;

            finalData.storedMoney = 0;
            manager.put(finalData);
            msg(p, "money-received", "%money%", SpawnerGUI.formatMoney(finalMoney), "%mob%", SpawnerManager.pretty(finalData.entityType));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);

            p.closeInventory();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                giveMoney(p, finalMoney);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getGui().open(p, finalData));
            });
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
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c));
            return true;
        }
        
        if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + p.getName() + " " + (long) amount));
            return true;
        }
        return false;
    }
}
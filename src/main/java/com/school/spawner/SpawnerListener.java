package com.school.spawner;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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

    // ── Place spawner ──────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) return;
        ItemStack item = e.getItemInHand();
        EntityType type = manager.getMobFromItem(item);
        if (type == null) return; // vanilla spawner, ignore

        Player p = e.getPlayer();
        int level = manager.getLevelFromItem(item);
        UUID owner = manager.getOwnerFromItem(item);
        String ownerName = manager.getOwnerNameFromItem(item);
        if (owner == null) { owner = p.getUniqueId(); ownerName = p.getName(); }

        SpawnerData data = new SpawnerData(e.getBlock().getLocation(), type, Math.max(1, level), owner, ownerName);
        manager.put(data);

        msg(p, "spawner-placed", "%mob%", SpawnerManager.pretty(type), "%level%", String.valueOf(level));
    }

    // ── Break spawner ──────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData data = manager.getAt(b.getLocation());
        if (data == null) return; // vanilla

        Player p = e.getPlayer();
        // Cho phép đập nếu là owner hoặc có admin
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

    // ── Right-click spawner ───────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;

        Player p = e.getPlayer();
        SpawnerData data = manager.getAt(b.getLocation());
        if (data == null) return; // vanilla spawner

        e.setCancelled(true);

        // check owner
        boolean isOwner = p.getUniqueId().equals(data.owner);
        boolean isAdmin = p.hasPermission("schoolspawner.admin");

        ItemStack inHand = p.getInventory().getItemInMainHand();
        EntityType handMob = manager.getMobFromItem(inHand);

        // Merge attempt: shift + holding a school spawner with same mob
        if (p.isSneaking() && handMob != null && plugin.getConfig().getBoolean("settings.allow-merge", true)) {
            if (handMob != data.entityType) {
                msg(p, "merge-fail-different");
                return;
            }
            int maxLevel = plugin.getConfig().getInt("settings.max-level", 100);
            int handLevel = manager.getLevelFromItem(inHand);
            UUID handOwner = manager.getOwnerFromItem(inHand);
            if (handOwner != null && !handOwner.equals(data.owner) && !isAdmin) {
                msg(p, "not-owner");
                return;
            }
            if (data.level + handLevel > maxLevel) {
                msg(p, "merge-fail-max");
                return;
            }
            // consume one from hand
            if (inHand.getAmount() > 1) inHand.setAmount(inHand.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);

            data.level += handLevel;
            data.lastGenerate = System.currentTimeMillis();
            manager.put(data);

            msg(p, "spawner-merged", "%mob%", SpawnerManager.pretty(data.entityType), "%level%", String.valueOf(data.level));
            // particles
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            return;
        }

        // GUI open
        if (plugin.getConfig().getBoolean("settings.owner-only-gui", true) && !isOwner && !isAdmin) {
            msg(p, "not-owner");
            return;
        }
        plugin.getGui().open(p, data);
    }

    // ── GUI click ────────────────────────────────────────────────
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title());
        // safer: check our custom title prefix via component compare
        // Simplify: rely on unique title content
        String plain = LegacyComponentSerializer.legacySection().serialize(e.getView().title());
        if (!plain.contains("Lồng")) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        // Tìm spawner data bằng vị trí khoá theo view — không có; dùng item name
        // Thay vào đó ta lưu location vào NBT của item info? Đơn giản: dùng map tạm thời qua player open.
        // Tối ưu: dùng PDC trong item GOLD_INGOT chứa vị trí.
        // Để đơn giản và ổn định: ta scan lại các spawner của player, và kiểm tra block dưới chân... 
        // => Cách chính xác: dùng item PDC chứa loc key.

        // Ta cần lấy loc key từ item money/info — nhưng code trên chưa gắn.
        // → Sửa: GUI gọi với data, nhưng InventoryClick chỉ nhận title. Ta sẽ dùng biện pháp khác:
        // Player mở GUI tại 1 block spawner nhất định. Lưu tạm vào map playerSpawner.
        // (Cách này an toàn với đa số trường hợp)
    }
}

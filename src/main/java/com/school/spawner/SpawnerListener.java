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
        if (b == null || b.getType() != Material.SPAWNER)

package com.school.spawner;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SchoolExpansion extends PlaceholderExpansion {

    private final SchoolSpawner plugin;

    public SchoolExpansion(SchoolSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "schoolspawner";
    }

    @Override
    public @NotNull String getAuthor() {
        return "School";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) return "";

        Player p = player.getPlayer();
        if (p == null) return "";

        // Placeholder: %schoolspawner_total_income%
        if (params.equalsIgnoreCase("total_income")) {
            double total = 0;
            Location pLoc = p.getLocation();

            for (SpawnerData data : plugin.getManager().all()) {
                if (data.owner != null && data.owner.equals(p.getUniqueId())) {
                    // Chỉ tính các lồng trong phạm vi 100 block
                    if (data.location.getWorld().equals(pLoc.getWorld()) && data.location.distance(pLoc) <= 100.0) {
                        double income = data.basePrice();
                        income *= plugin.getManager().getPermissionMultiplier(p);
                        total += income;
                    }
                }
            }
            return SpawnerGUI.formatMoney(total);
        }

        // Placeholder: %schoolspawner_total_spawners% (Tổng số lồng trong 100 block)
        if (params.equalsIgnoreCase("total_spawners")) {
            int count = 0;
            Location pLoc = p.getLocation();

            for (SpawnerData data : plugin.getManager().all()) {
                if (data.owner != null && data.owner.equals(p.getUniqueId())) {
                    if (data.location.getWorld().equals(pLoc.getWorld()) && data.location.distance(pLoc) <= 100.0) {
                        count++;
                    }
                }
            }
            return String.valueOf(count);
        }

        return null;
    }
}
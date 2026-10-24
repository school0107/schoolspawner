package com.school.spawner;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
        return true; // Giữ PAPI không bị unregister khi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("total_income")) {
            double total = 0;
            for (SpawnerData data : plugin.getManager().all()) {
                if (data.owner != null && data.owner.equals(player.getUniqueId())) {
                    double income = data.basePrice();
                    if (player.isOnline() && player.getPlayer() != null) {
                        income *= plugin.getManager().getPermissionMultiplier(player.getPlayer());
                    }
                    total += income;
                }
            }
            return SpawnerGUI.formatMoney(total);
        }

        if (params.equalsIgnoreCase("total_spawners")) {
            int count = 0;
            for (SpawnerData data : plugin.getManager().all()) {
                if (data.owner != null && data.owner.equals(player.getUniqueId())) {
                    count++;
                }
            }
            return String.valueOf(count);
        }

        return null;
    }
}
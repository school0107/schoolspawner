package com.school.spawner;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.UUID;

public class SpawnerData {

    public Location location;
    public EntityType entityType;
    public int level;
    public UUID owner;
    public String ownerName;
    public double storedMoney;
    public long lastGenerate;

    public SpawnerData(Location location, EntityType entityType, int level, UUID owner, String ownerName) {
        this.location = location;
        this.entityType = entityType;
        this.level = Math.max(1, level);
        this.owner = owner;
        this.ownerName = ownerName;
        this.storedMoney = 0;
        this.lastGenerate = System.currentTimeMillis();
    }

    public double basePrice() {
        SchoolSpawner plugin = SchoolSpawner.getInstance();
        double base = plugin.getConfig().getDouble("mob-prices." + entityType.name(), 3);
        double mult = plugin.getConfig().getDouble("settings.level-multiplier", 1.0);
        return base * level * mult;
    }
}

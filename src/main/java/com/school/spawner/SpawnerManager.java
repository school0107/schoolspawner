package com.school.spawner;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {
    private final SchoolSpawner plugin;
    private final Map<String, SpawnerData> spawners = new ConcurrentHashMap<>();
    private final File dataFile;
    private final NamespacedKey keyMob, keyLevel, keyOwner, keyOwnerName;
    private BukkitTask task;

    public SpawnerManager(SchoolSpawner plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spawners.yml");
        this.keyMob = new NamespacedKey(plugin, "mob");
        this.keyLevel = new NamespacedKey(plugin, "level");
        this.keyOwner = new NamespacedKey(plugin, "owner");
        this.keyOwnerName = new NamespacedKey(plugin, "owner_name");
        loadAll();
    }

    public NamespacedKey getKeyMob() { return keyMob; }
    public NamespacedKey getKeyLevel() { return keyLevel; }
    public NamespacedKey getKeyOwner() { return keyOwner; }
    public NamespacedKey getKeyOwnerName() { return keyOwnerName; }

    public static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void startTask() {
        long interval = Math.max(1, plugin.getConfig().getLong("settings.generate-interval", 3)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stopTask() {
        if (task != null) task.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double intervalMs = Math.max(1, plugin.getConfig().getLong("settings.generate-interval", 3)) * 1000.0;
        double maxCap = plugin.getConfig().getDouble("settings.max-money-per-spawner", 100_000_000);

        for (SpawnerData data : spawners.values()) {
            if (data.location.getWorld() == null) continue;
            if (!data.location.getWorld().isChunkLoaded(data.location.getBlockX() >> 4, data.location.getBlockZ() >> 4)) continue;
            if (now - data.lastGenerate < intervalMs) continue;
            data.lastGenerate = now;

            double money = data.basePrice();
            Player owner = Bukkit.getPlayer(data.owner);
            if (owner != null && owner.isOnline()) {
                money *= getPermissionMultiplier(owner);
            }
            data.storedMoney = Math.min(maxCap, data.storedMoney + money);
        }
    }

    public double getPermissionMultiplier(Player p) {
        int best = 1;
        for (int i = 10; i >= 1; i--) {
            if (p.hasPermission("vspawner." + i)) { best = i; break; }
        }
        return plugin.getConfig().getDouble("permission-multipliers." + best, 1.0);
    }

    public ItemStack createSpawnerItem(EntityType type, int level, UUID owner, String ownerName) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = "&#FF55FF✦ Lồng " + pretty(type) + " &#AAAAAA[Cấp " + level + "]";
        meta.displayName(TextUtil.color(name));
        meta.lore(Arrays.asList(
                TextUtil.color("&#55FFFF▸ Thực thể: &f" + pretty(type)),
                TextUtil.color("&#55FFFF▸ Cấp: &#FFAA00" + level),
                TextUtil.color("&#55FFFF▸ Sinh tiền: &#55FF55" + String.format("%.1f", baseWithLevel(type, level)) + "$ &7/ 3s"),
                TextUtil.color("&#55FFFF▸ Chủ sở hữu: &#FFAA00" + (ownerName == null ? "Server" : ownerName)),
                TextUtil.color("&8"),
                TextUtil.color("&#FFFF55Shift + Chuột phải &7để gộp"),
                TextUtil.color("&#55FF55Chuột phải &7để mở menu"),
                TextUtil.color("&#FF5555Đập để thu hồi")
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyMob, PersistentDataType.STRING, type.name());
        pdc.set(keyLevel, PersistentDataType.INTEGER, level);
        if (owner != null) pdc.set(keyOwner, PersistentDataType.STRING, owner.toString());
        if (ownerName != null) pdc.set(keyOwnerName, PersistentDataType.STRING, ownerName);
        item.setItemMeta(meta);
        return item;
    }

    public double baseWithLevel(EntityType type, int level) {
        double base = plugin.getConfig().getDouble("mob-prices." + type.name(), 3);
        double mult = plugin.getConfig().getDouble("settings.level-multiplier", 1.0);
        return base * level * mult;
    }

    public EntityType getMobFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(keyMob, PersistentDataType.STRING);
        if (s == null) return null;
        try { return EntityType.valueOf(s); } catch (Exception e) { return null; }
    }

    public int getLevelFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer i = item.getItemMeta().getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return i == null ? 0 : i;
    }

    public UUID getOwnerFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public String getOwnerNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyOwnerName, PersistentDataType.STRING);
    }

    public static String pretty(EntityType type) {
        String s = type.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public SpawnerData getAt(Location loc) { return spawners.get(locKey(loc)); }
    
    public void put(SpawnerData data) {
        spawners.put(locKey(data.location), data);
        saveAll();
    }

    public void remove(Location loc) {
        spawners.remove(locKey(loc));
        saveAll();
    }

    public Collection<SpawnerData> all() { return spawners.values(); }

    public void saveAll() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, SpawnerData> e : spawners.entrySet()) {
            SpawnerData d = e.getValue();
            String base = "spawners." + e.getKey().replace(":", "_") + ".";
            yml.set(base + "world", d.location.getWorld().getName());
            yml.set(base + "x", d.location.getBlockX());
            yml.set(base + "y", d.location.getBlockY());
            yml.set(base + "z", d.location.getBlockZ());
            yml.set(base + "type", d.entityType.name());
            yml.set(base + "level", d.level);
            yml.set(base + "owner", d.owner == null ? null : d.owner.toString());
            yml.set(base + "ownerName", d.ownerName);
            yml.set(base + "money", d.storedMoney);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save spawners.yml: " + ex.getMessage());
        }
    }

    public void loadAll() {
        spawners.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = yml.getConfigurationSection("spawners");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            String worldName = s.getString("world");
            World w = worldName == null ? null : Bukkit.getWorld(worldName);
            if (w == null) continue;
            int x = s.getInt("x"), y = s.getInt("y"), z = s.getInt("z");
            EntityType type;
            try { type = EntityType.valueOf(s.getString("type", "ZOMBIE")); } catch (Exception ex) { continue; }
            int lvl = Math.max(1, s.getInt("level", 1));
            UUID owner = null;
            String os = s.getString("owner");
            if (os != null) try { owner = UUID.fromString(os); } catch (Exception ignored) {}
            String ownerName = s.getString("ownerName", "Server");
            SpawnerData d = new SpawnerData(new Location(w, x, y, z), type, lvl, owner, ownerName);
            d.storedMoney = s.getDouble("money", 0);
            spawners.put(locKey(d.location), d);
        }
    }

    public void reload() {
        saveAll();
        loadAll();
        stopTask();
        startTask();
    }
}
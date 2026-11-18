package com.school.spawner;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {
    private final SchoolSpawner plugin;
    private final Map<String, SpawnerData> spawners = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerDropBonus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerTempBonusExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerTempBonusValue = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerCapacityBonus = new ConcurrentHashMap<>();
    
    // Biến sự kiện Giờ Vàng
    private double eventMultiplier = 1.0;
    private long eventEndTime = 0;
    
    // Biến lịch
    private int lastScheduleCheckMinute = -1;
    private int lastScheduleCheckHour = -1;

    private final File dataFile;
    private final File playerDataFile;
    private final NamespacedKey keyMob, keyLevel, keyOwner, keyOwnerName;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private boolean needsSave = false;

    public SpawnerManager(SchoolSpawner plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spawners.yml");
        this.playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        this.keyMob = new NamespacedKey(plugin, "mob");
        this.keyLevel = new NamespacedKey(plugin, "level");
        this.keyOwner = new NamespacedKey(plugin, "owner");
        this.keyOwnerName = new NamespacedKey(plugin, "owner_name");
        loadAll();
        loadPlayerData();
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
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);

        long saveInterval = Math.max(10, plugin.getConfig().getLong("settings.auto-save-interval", 60)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (needsSave) {
                saveToDisk();
                savePlayerData();
                needsSave = false;
            }
        }, saveInterval, saveInterval);
    }

    public void stopTask() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double intervalMs = Math.max(1, plugin.getConfig().getLong("settings.generate-interval", 3)) * 1000.0;
        double baseCap = plugin.getConfig().getDouble("settings.max-money-per-spawner", 100_000_000);

        // Kiểm tra lịch Giờ Vàng
        checkSchedule();

        for (SpawnerData data : spawners.values()) {
            if (data.location.getWorld() == null) continue;
            if (!data.location.getWorld().isChunkLoaded(data.location.getBlockX() >> 4, data.location.getBlockZ() >> 4)) continue;
            if (now - data.lastGenerate < intervalMs) continue;
            data.lastGenerate = now;

            double maxCap = baseCap;
            if (data.owner != null) {
                maxCap += playerCapacityBonus.getOrDefault(data.owner, 0.0);
            }

            double money = data.basePrice();
            
            // Áp dụng hệ số sự kiện Giờ Vàng
            if (isEventActive()) {
                money *= getEventMultiplier();
            }
            
            data.storedMoney = Math.min(maxCap, data.storedMoney + money);
        }
    }

    // --- KIỂM TRA LỊCH GIỜ VÀNG ---
    private void checkSchedule() {
        if (!plugin.getConfig().getBoolean("golden-hour-schedule.enabled", false)) return;
        if (isEventActive()) return; // Đang diễn ra thì không check nữa

        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();

        // Chỉ check 1 lần mỗi phút để tránh lag
        if (hour == lastScheduleCheckHour && minute == lastScheduleCheckMinute) return;
        lastScheduleCheckHour = hour;
        lastScheduleCheckMinute = minute;

        List<String> days = plugin.getConfig().getStringList("golden-hour-schedule.days");
        if (days.isEmpty()) return;

        DayOfWeek currentDay = now.getDayOfWeek();
        boolean isDayMatch = false;
        for (String day : days) {
            try {
                if (DayOfWeek.valueOf(day.toUpperCase()) == currentDay) {
                    isDayMatch = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (!isDayMatch) return;

        String startTimeStr = plugin.getConfig().getString("golden-hour-schedule.start-time", "20:00");
        try {
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            if (hour == startTime.getHour() && minute == startTime.getMinute()) {
                long durationMinutes = plugin.getConfig().getLong("golden-hour-schedule.duration-minutes", 30);
                double multiplier = plugin.getConfig().getDouble("golden-hour-schedule.multiplier", 2.0);
                
                startGoldenHour(multiplier, durationMinutes * 60 * 1000);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid golden-hour-schedule start-time format: " + startTimeStr);
        }
    }

    // --- CÁC HÀM QUẢN LÝ SỰ KIỆN GIỜ VÀNG ---
    public boolean isEventActive() {
        return System.currentTimeMillis() < eventEndTime;
    }

    public double getEventMultiplier() {
        return eventMultiplier;
    }

    public long getEventEndTime() {
        return eventEndTime;
    }

    public void startGoldenHour(double multiplier, long durationMillis) {
        this.eventMultiplier = multiplier;
        this.eventEndTime = System.currentTimeMillis() + durationMillis;
        
        String timeStr = formatDuration(durationMillis);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(TextUtil.color("&#FFD700✦ &#FFAA00&lGIỜ VÀNG &#FFD700✦ &fĐÃ BẮT ĐẦU! &#AAAAAA(" + timeStr + ")"));
            p.sendMessage(TextUtil.color("&#55FF55Tất cả lồng sẽ sinh ra gấp &#FFAA00" + multiplier + "x &#55FF55tiền trong thời gian này!"));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            p.sendTitle(TextUtil.color("&#FFD700✦ GIỜ VÀNG ✦"), TextUtil.color("&#55FF55Thu nhập lồng x" + multiplier + " trong " + timeStr), 10, 70, 20);
        }
    }

    public void stopGoldenHour() {
        this.eventMultiplier = 1.0;
        this.eventEndTime = 0;
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(TextUtil.color("&#FF5555✦ &#FFAA00&lGIỜ VÀNG &#FF5555ĐÃ KẾT THÚC!"));
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) return hours + " giờ " + (minutes % 60) + " phút";
        if (minutes > 0) return minutes + " phút " + (seconds % 60) + " giây";
        return seconds + " giây";
    }

    // --- CÁC HÀM KHÁC ---
    public double getPermissionMultiplier(Player p) {
        int best = 1;
        for (int i = 10; i >= 1; i--) {
            if (p.hasPermission("vspawner." + i)) { best = i; break; }
        }
        return plugin.getConfig().getDouble("permission-multipliers." + best, 1.0);
    }

    public double getPlayerDropBonus(UUID uuid) {
        double permBonus = playerDropBonus.getOrDefault(uuid, 0.0);
        double tempBonus = 0.0;
        Long expiry = playerTempBonusExpiry.get(uuid);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            tempBonus = playerTempBonusValue.getOrDefault(uuid, 0.0);
        }
        return permBonus + tempBonus;
    }

    public void addPlayerDropBonus(UUID uuid, double amount) {
        double current = playerDropBonus.getOrDefault(uuid, 0.0);
        playerDropBonus.put(uuid, Math.min(1.0, current + amount));
        needsSave = true;
    }

    public void addTempPlayerDropBonus(UUID uuid, double amount, long durationMillis) {
        long now = System.currentTimeMillis();
        Long currentExpiry = playerTempBonusExpiry.get(uuid);
        double currentValue = 0.0;
        if (currentExpiry != null && currentExpiry > now) {
            currentValue = playerTempBonusValue.getOrDefault(uuid, 0.0);
        }
        long newExpiry = (currentExpiry != null && currentExpiry > now) ? currentExpiry + durationMillis : now + durationMillis;
        playerTempBonusExpiry.put(uuid, newExpiry);
        playerTempBonusValue.put(uuid, Math.min(1.0, currentValue + amount));
        needsSave = true;
    }

    public double getPlayerCapacityBonus(UUID uuid) {
        return playerCapacityBonus.getOrDefault(uuid, 0.0);
    }

    public void addPlayerCapacityBonus(UUID uuid, double amount) {
        double current = getPlayerCapacityBonus(uuid);
        double maxCap = plugin.getConfig().getDouble("capacity-charm.max-capacity-bonus", 500000000);
        if (current >= maxCap) return;
        double newValue = Math.min(maxCap, current + amount);
        playerCapacityBonus.put(uuid, newValue);
        needsSave = true;
    }

    public ItemStack createSpawnerItem(EntityType type, int level, UUID owner, String ownerName) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getConfig().getString("spawner-item.name", "&#FF55FF✦ Lồng %mob% &#AAAAAA[Cấp %level%]")
                .replace("%mob%", pretty(type))
                .replace("%level%", String.valueOf(level));
        meta.displayName(TextUtil.color(name));

        List<String> loreRaw = plugin.getConfig().getStringList("spawner-item.lore");
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(TextUtil.color(line
                    .replace("%mob%", pretty(type))
                    .replace("%level%", String.valueOf(level))
                    .replace("%rate%", String.format("%.1f", baseWithLevel(type, level)))
                    .replace("%owner%", ownerName == null ? "Server" : ownerName)
            ));
        }
        meta.lore(lore);

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
        needsSave = true;
    }

    public void remove(Location loc) {
        spawners.remove(locKey(loc));
        needsSave = true;
    }

    public Collection<SpawnerData> all() { return spawners.values(); }

    public void saveToDisk() {
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
            Location loc = new Location(w, x, y, z);
            SpawnerData d = new SpawnerData(loc, type, lvl, owner, ownerName);
            d.storedMoney = s.getDouble("money", 0);
            spawners.put(locKey(d.location), d);

            if (loc.getBlock().getType() == Material.SPAWNER) {
                BlockState state = loc.getBlock().getState();
                if (state instanceof CreatureSpawner spawner) {
                    if (spawner.getSpawnedType() != type) {
                        spawner.setSpawnedType(type);
                        spawner.update(true, false);
                    }
                }
            }
        }
    }

    public void savePlayerData() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : playerDropBonus.entrySet()) {
            yml.set("players.perm." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Long> entry : playerTempBonusExpiry.entrySet()) {
            yml.set("players.temp_expiry." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Double> entry : playerTempBonusValue.entrySet()) {
            yml.set("players.temp_val." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Double> entry : playerCapacityBonus.entrySet()) {
            yml.set("players.capacity." + entry.getKey().toString(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(playerDataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save playerdata.yml: " + ex.getMessage());
        }
    }

    public void loadPlayerData() {
        playerDropBonus.clear();
        playerTempBonusExpiry.clear();
        playerTempBonusValue.clear();
        playerCapacityBonus.clear();
        if (!playerDataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(playerDataFile);
        
        ConfigurationSection permSec = yml.getConfigurationSection("players.perm");
        if (permSec != null) {
            for (String key : permSec.getKeys(false)) {
                try { playerDropBonus.put(UUID.fromString(key), permSec.getDouble(key)); } catch (Exception ignored) {}
            }
        }
        ConfigurationSection expSec = yml.getConfigurationSection("players.temp_expiry");
        if (expSec != null) {
            for (String key : expSec.getKeys(false)) {
                try { playerTempBonusExpiry.put(UUID.fromString(key), expSec.getLong(key)); } catch (Exception ignored) {}
            }
        }
        ConfigurationSection valSec = yml.getConfigurationSection("players.temp_val");
        if (valSec != null) {
            for (String key : valSec.getKeys(false)) {
                try { playerTempBonusValue.put(UUID.fromString(key), valSec.getDouble(key)); } catch (Exception ignored) {}
            }
        }
        ConfigurationSection capSec = yml.getConfigurationSection("players.capacity");
        if (capSec != null) {
            for (String key : capSec.getKeys(false)) {
                try { playerCapacityBonus.put(UUID.fromString(key), capSec.getDouble(key)); } catch (Exception ignored) {}
            }
        }
    }

    public void reload() {
        stopTask();
        saveToDisk();
        savePlayerData();
        loadAll();
        loadPlayerData();
        startTask();
    }
}
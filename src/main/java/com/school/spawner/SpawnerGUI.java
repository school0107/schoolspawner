package com.school.spawner;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class SpawnerGUI {
    private final SchoolSpawner plugin;
    private final SpawnerManager manager;
    private final NamespacedKey locKey;

    public SpawnerGUI(SchoolSpawner plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
        this.locKey = new NamespacedKey(plugin, "gui_loc");
    }

    public NamespacedKey getLocKey() { return locKey; }

    public void open(Player p, SpawnerData data) {
        FileConfiguration cfg = plugin.getConfig();
        String titleStr = cfg.getString("gui.title", "&#FF55FF✦ &fLồng %mob% &#AAAAAA[Cấp %level%]")
                .replace("%mob%", SpawnerManager.pretty(data.entityType))
                .replace("%level%", String.valueOf(data.level));
        
        Component title = TextUtil.color(titleStr);
        Inventory inv = Bukkit.createInventory(null, 27, title);
        String keyStr = SpawnerManager.locKey(data.location);

        // Ô Nhận Tiền
        Material moneyMat = Material.matchMaterial(cfg.getString("gui.money-item", "GOLD_INGOT"));
        if (moneyMat == null) moneyMat = Material.GOLD_INGOT;
        ItemStack money = new ItemStack(moneyMat);
        ItemMeta mm = money.getItemMeta();
        if (mm != null) {
            mm.displayName(TextUtil.color(cfg.getString("gui.money-name", "&#55FF55💰 NHẬN TIỀN")));
            List<Component> moneyLore = new ArrayList<>();
            for (String line : cfg.getStringList("gui.money-lore")) {
                moneyLore.add(TextUtil.color(line
                        .replace("%money%", formatMoney(data.storedMoney))
                        .replace("%rate%", formatMoney(data.basePrice()))
                        .replace("%mob%", SpawnerManager.pretty(data.entityType))
                        .replace("%level%", String.valueOf(data.level))));
            }
            mm.lore(moneyLore);
            mm.getPersistentDataContainer().set(locKey, PersistentDataType.STRING, keyStr);
            money.setItemMeta(mm);
        }
        int moneySlot = cfg.getInt("gui.money-slot", 11);
        if (moneySlot >= 0 && moneySlot < 27) inv.setItem(moneySlot, money);

        // Ô Thông Tin
        Material infoMat = Material.matchMaterial(cfg.getString("gui.info-item", "BOOK"));
        if (infoMat == null) infoMat = Material.BOOK;
        ItemStack info = new ItemStack(infoMat);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(TextUtil.color(cfg.getString("gui.info-name", "&#55FFFF📖 THÔNG TIN LỒNG")));
            List<Component> infoLore = new ArrayList<>();
            for (String line : cfg.getStringList("gui.info-lore")) {
                infoLore.add(TextUtil.color(line
                        .replace("%mob%", SpawnerManager.pretty(data.entityType))
                        .replace("%level%", String.valueOf(data.level))
                        .replace("%max_level%", String.valueOf(cfg.getInt("settings.max-level", 100)))
                        .replace("%owner%", data.ownerName == null ? "Server" : data.ownerName)
                        .replace("%rate%", formatMoney(data.basePrice()))));
            }
            im.lore(infoLore);
            info.setItemMeta(im);
        }
        int infoSlot = cfg.getInt("gui.info-slot", 15);
        if (infoSlot >= 0 && infoSlot < 27) inv.setItem(infoSlot, info);

        // Kính trang trí (nếu bật)
        if (cfg.getBoolean("gui.use-glass", false)) {
            Material glassMat = Material.matchMaterial(cfg.getString("gui.glass-item", "PURPLE_STAINED_GLASS_PANE"));
            if (glassMat == null) glassMat = Material.PURPLE_STAINED_GLASS_PANE;
            ItemStack glass = new ItemStack(glassMat);
            ItemMeta gm = glass.getItemMeta();
            if (gm != null) {
                gm.displayName(TextUtil.color("&r"));
                glass.setItemMeta(gm);
            }
            for (int i = 0; i < 27; i++) {
                if (inv.getItem(i) == null) inv.setItem(i, glass);
            }
        }

        p.openInventory(inv);
    }

    public static String formatMoney(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.2fK", v / 1_000);
        return String.format("%.2f", v);
    }
}
package com.school.spawner;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
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
        Component title = TextUtil.color("&#FF55FF✦ &fLồng " + SpawnerManager.pretty(data.entityType) + " &#AAAAAA[Cấp " + data.level + "]");
        // Chỉ 9 slot, bỏ kính
        Inventory inv = Bukkit.createInventory(null, 9, title);
        String keyStr = SpawnerManager.locKey(data.location);

        // Slot 2: Nhận tiền
        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta mm = money.getItemMeta();
        mm.displayName(TextUtil.color("&#55FF55💰 NHẬN TIỀN"));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.color("&#AAAAAATiền đã tích luỹ:"));
        lore.add(TextUtil.color("&#FFAA00➤ &f" + formatMoney(data.storedMoney) + " &7$"));
        lore.add(TextUtil.color("&8"));
        lore.add(TextUtil.color("&#55FFFFSinh mỗi 3s: &a" + formatMoney(data.basePrice()) + " $"));
        lore.add(TextUtil.color("&8"));
        lore.add(TextUtil.color("&#FFFF55▶ Click để nhận!"));
        mm.lore(lore);
        mm.getPersistentDataContainer().set(locKey, PersistentDataType.STRING, keyStr);
        money.setItemMeta(mm);
        inv.setItem(2, money);

        // Slot 6: Thông tin
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(TextUtil.color("&#55FFFF📖 THÔNG TIN LỒNG"));
        im.lore(Arrays.asList(
                TextUtil.color("&#55FFFF▸ Thực thể: &f" + SpawnerManager.pretty(data.entityType)),
                TextUtil.color("&#55FFFF▸ Cấp: &#FFAA00" + data.level),
                TextUtil.color("&#55FFFF▸ Chủ sở hữu: &#FFAA00" + (data.ownerName == null ? "Server" : data.ownerName)),
                TextUtil.color("&#55FFFF▸ Tiền mỗi 3s: &#55FF55" + formatMoney(data.basePrice()) + " $")
        ));
        info.setItemMeta(im);
        inv.setItem(6, info);

        p.openInventory(inv);
    }

    public static String formatMoney(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.2fK", v / 1_000);
        return String.format("%.2f", v);
    }
}
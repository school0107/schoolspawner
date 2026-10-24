package com.school.spawner;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpawnerGUI {

    private final SchoolSpawner plugin;
    private final SpawnerManager manager;

    public SpawnerGUI(SchoolSpawner plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    public void open(Player p, SpawnerData data) {
        Component title = TextUtil.color("&#FF55FF✦ &fLồng " + SpawnerManager.pretty(data.entityType) + " &#AAAAAA[Cấp " + data.level + "]");
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // slot 11 — nhận tiền
        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta mm = money.getItemMeta();
        mm.displayName(TextUtil.color("&#55FF55💰 NHẬN TIỀN"));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.color("&#AAAAAATiền đã tích luỹ từ lồng:"));
        lore.add(TextUtil.color("&#FFAA00➤ &f" + formatMoney(data.storedMoney) + " &7$"));
        lore.add(TextUtil.color("&8"));
        lore.add(TextUtil.color("&#55FFFFSinh mỗi 3s: &a" + formatMoney(data.basePrice()) + " $"));
        lore.add(TextUtil.color("&8"));
        lore.add(TextUtil.color("&#FFFF55▶ Click để nhận!"));
        mm.lore(lore);
        money.setItemMeta(mm);
        inv.setItem(11, money);

        // slot 15 — thông tin
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(TextUtil.color("&#55FFFF📖 THÔNG TIN LỒNG"));
        im.lore(Arrays.asList(
                TextUtil.color("&#55FFFF▸ Thực thể: &f" + SpawnerManager.pretty(data.entityType)),
                TextUtil.color("&#55FFFF▸ Cấp: &#FFAA00" + data.level + " &#AAAAAA/ &#FFAA00" + plugin.getConfig().getInt("settings.max-level", 100)),
                TextUtil.color("&#55FFFF▸ Chủ sở hữu: &#FFAA00" + (data.ownerName == null ? "Server" : data.ownerName)),
                TextUtil.color("&#55FFFF▸ Tiền mỗi 3s: &#55FF55" + formatMoney(data.basePrice()) + " $"),
                TextUtil.color("&8"),
                TextUtil.color("&#AAAAAAVị trí: &#55FF55" + data.location.getBlockX() + ", " + data.location.getBlockY() + ", " + data.location.getBlockZ())
        ));
        info.setItemMeta(im);
        inv.setItem(15, info);

        // decorative
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(TextUtil.color("&r"));
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
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

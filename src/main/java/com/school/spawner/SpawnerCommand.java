package com.school.spawner;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private final SchoolSpawner plugin;

    public SpawnerCommand(SchoolSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            sender.sendMessage(TextUtil.color(prefix + "&#55FFFF✦ SchoolSpawner &#AAAAAAv" + plugin.getDescription().getVersion()));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss give <player> <mob> [level] &#AAAAAA- Trao lồng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss giveitem <mob> [level] &#AAAAAA- Tự trao lồng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss givecharm <player> [amount] &#AAAAAA- Trao Bùa Vĩnh Viễn"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss givetempcharm <player> [amount] &#AAAAAA- Trao Bùa Thời Hạn"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss givecapcharm <player> [amount] &#AAAAAA- Trao Bùa Dung Lượng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss giveeventitem <player> [amount] &#AAAAAA- Trao Vật phẩm Giờ Vàng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss goldenhour start <minutes> &#AAAAAA- Bắt đầu Giờ Vàng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss goldenhour stop &#AAAAAA- Dừng Giờ Vàng"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss reload &#AAAAAA- Reload config"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss list &#AAAAAA- Danh sách mob"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "goldenhour" -> {
                if (!sender.hasPermission("schoolspawner.admin")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss goldenhour <start/stop> [time]"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("stop")) {
                    plugin.getManager().stopGoldenHour();
                    sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã dừng Giờ Vàng!"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("start")) {
                    long minutes = 10;
                    if (args.length >= 3) {
                        try { minutes = Long.parseLong(args[2]); } catch (NumberFormatException ignored) {}
                    }
                    String summoner = sender instanceof Player p ? p.getName() : "Server";
                    plugin.getManager().startGoldenHour(2.0, minutes * 60 * 1000, summoner);
                    sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã bắt đầu Giờ Vàng trong " + minutes + " phút!"));
                    return true;
                }
                return true;
            }
            case "giveeventitem" -> {
                if (!sender.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss giveeventitem <player> [amount]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Player not found!"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                }

                ItemStack charm = new ItemStack(Material.NETHER_STAR, amount);
                ItemMeta meta = charm.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.color("&#FFD700✦ VẬT PHẨM TRIỆU HỒI GIỜ VÀNG ✦"));
                    List<String> lore = new ArrayList<>();
                    lore.add(TextUtil.color("&#AAAAAASử dụng để triệu hồi Giờ Vàng (X2)"));
                    lore.add(TextUtil.color("&#AAAAAAtrong vòng 10 phút."));
                    lore.add(TextUtil.color("&8"));
                    lore.add(TextUtil.color("&#55FF55▶ Chuột phải để sử dụng!"));
                    meta.lore(lore.stream().map(TextUtil::color).collect(Collectors.toList()));
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lucky_charm"), PersistentDataType.STRING, "goldenhour_item");
                    charm.setItemMeta(meta);
                }

                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItemNaturally(target.getLocation(), charm);
                } else {
                    target.getInventory().addItem(charm);
                }
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã trao &e" + amount + " Vật phẩm Giờ Vàng &acho &e" + target.getName()));
                target.sendMessage(TextUtil.color(prefix + "&#55FF55Bạn đã nhận &e" + amount + " Vật phẩm Giờ Vàng!"));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("schoolspawner.reload")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã reload config!"));
                return true;
            }
            case "list" -> {
                sender.sendMessage(TextUtil.color(prefix + "&#55FFFFDanh sách mob hợp lệ:"));
                List<String> names = new ArrayList<>();
                for (EntityType t : EntityType.values()) {
                    if (t.isAlive() && t.isSpawnable()) names.add(t.name());
                }
                sender.sendMessage(TextUtil.color("&#AAAAAA" + String.join(", ", names)));
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss give <player> <mob> [level]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Player not found!"));
                    return true;
                }
                EntityType type;
                try { type = EntityType.valueOf(args[2].toUpperCase()); }
                catch (Exception e) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Invalid mob: " + args[2]));
                    return true;
                }
                int level = 1;
                if (args.length >= 4) {
                    try { level = Math.max(1, Integer.parseInt(args[3])); }
                    catch (NumberFormatException e) {
                        sender.sendMessage(TextUtil.color(prefix + "&#FF5555Invalid level!"));
                        return true;
                    }
                }
                ItemStack item = plugin.getManager().createSpawnerItem(type, level, target.getUniqueId(), target.getName());
                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItemNaturally(target.getLocation(), item);
                } else {
                    target.getInventory().addItem(item);
                }
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã trao lồng &e" + SpawnerManager.pretty(type) + " &aCấp &e" + level + " &acho &e" + target.getName()));
                target.sendMessage(TextUtil.color(prefix + "&#55FF55Bạn đã nhận lồng &e" + SpawnerManager.pretty(type) + " &aCấp &e" + level));
                return true;
            }
            case "giveitem" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(TextUtil.color(prefix + "&cPlayer only!"));
                    return true;
                }
                if (!p.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss giveitem <mob> [level]"));
                    return true;
                }
                EntityType type;
                try { type = EntityType.valueOf(args[1].toUpperCase()); }
                catch (Exception e) {
                    p.sendMessage(TextUtil.color(prefix + "&#FF5555Invalid mob!"));
                    return true;
                }
                int level = 1;
                if (args.length >= 3) {
                    try { level = Math.max(1, Integer.parseInt(args[2])); }
                    catch (NumberFormatException e) {
                        p.sendMessage(TextUtil.color(prefix + "&#FF5555Invalid level!"));
                        return true;
                    }
                }
                ItemStack item = plugin.getManager().createSpawnerItem(type, level, p.getUniqueId(), p.getName());
                if (p.getInventory().firstEmpty() == -1) {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                } else {
                    p.getInventory().addItem(item);
                }
                p.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã nhận lồng &e" + SpawnerManager.pretty(type) + " &aCấp &e" + level));
                return true;
            }
            case "givecharm" -> {
                if (!sender.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss givecharm <player> [amount]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Player not found!"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                }
                
                String charmMatName = plugin.getConfig().getString("lucky-charm.material", "SUNFLOWER");
                Material mat = Material.matchMaterial(charmMatName);
                if (mat == null) mat = Material.SUNFLOWER;

                ItemStack charm = new ItemStack(mat, amount);
                ItemMeta meta = charm.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.color(plugin.getConfig().getString("lucky-charm.name", "&#FFD700🍀 BÙA MAY MẮN")));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : plugin.getConfig().getStringList("lucky-charm.lore")) {
                        lore.add(TextUtil.color(line));
                    }
                    meta.lore(lore);
                    
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lucky_charm"), PersistentDataType.STRING, "perm");
                    charm.setItemMeta(meta);
                }
                
                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItemNaturally(target.getLocation(), charm);
                } else {
                    target.getInventory().addItem(charm);
                }
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã trao &e" + amount + " Bùa Vĩnh Viễn &acho &e" + target.getName()));
                target.sendMessage(TextUtil.color(prefix + "&#55FF55Bạn đã nhận &e" + amount + " Bùa Vĩnh Viễn!"));
                return true;
            }
            case "givetempcharm" -> {
                if (!sender.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss givetempcharm <player> [amount]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Player not found!"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                }

                String charmMatName = plugin.getConfig().getString("temp-lucky-charm.material", "CLOCK");
                Material mat = Material.matchMaterial(charmMatName);
                if (mat == null) mat = Material.CLOCK;

                ItemStack charm = new ItemStack(mat, amount);
                ItemMeta meta = charm.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.color(plugin.getConfig().getString("temp-lucky-charm.name", "&#FFAA00⏳ BÙA MAY MẮN")));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : plugin.getConfig().getStringList("temp-lucky-charm.lore")) {
                        lore.add(TextUtil.color(line));
                    }
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lucky_charm"), PersistentDataType.STRING, "temp");
                    charm.setItemMeta(meta);
                }

                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItemNaturally(target.getLocation(), charm);
                } else {
                    target.getInventory().addItem(charm);
                }
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã trao &e" + amount + " Bùa Thời Hạn &acho &e" + target.getName()));
                target.sendMessage(TextUtil.color(prefix + "&#55FF55Bạn đã nhận &e" + amount + " Bùa Thời Hạn!"));
                return true;
            }
            case "givecapcharm" -> {
                if (!sender.hasPermission("schoolspawner.give")) {
                    sender.sendMessage(TextUtil.color(prefix + "&cNo permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Usage: /ss givecapcharm <player> [amount]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(TextUtil.color(prefix + "&#FF5555Player not found!"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                }

                String charmMatName = plugin.getConfig().getString("capacity-charm.material", "DIAMOND");
                Material mat = Material.matchMaterial(charmMatName);
                if (mat == null) mat = Material.DIAMOND;

                ItemStack charm = new ItemStack(mat, amount);
                ItemMeta meta = charm.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.color(plugin.getConfig().getString("capacity-charm.name", "&#00E5FF💎 BÙA GIA TĂNG DUNG LƯỢNG")));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : plugin.getConfig().getStringList("capacity-charm.lore")) {
                        lore.add(TextUtil.color(line));
                    }
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lucky_charm"), PersistentDataType.STRING, "capacity");
                    charm.setItemMeta(meta);
                }

                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItemNaturally(target.getLocation(), charm);
                } else {
                    target.getInventory().addItem(charm);
                }
                sender.sendMessage(TextUtil.color(prefix + "&#55FF55✔ Đã trao &e" + amount + " Bùa Dung Lượng &acho &e" + target.getName()));
                target.sendMessage(TextUtil.color(prefix + "&#55FF55Bạn đã nhận &e" + amount + " Bùa Dung Lượng!"));
                return true;
            }
            default -> {
                sender.sendMessage(TextUtil.color(prefix + "&#FF5555Lệnh không hợp lệ. Dùng /ss help"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "giveitem", "givecharm", "givetempcharm", "givecapcharm", "giveeventitem", "goldenhour", "reload", "list")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givecharm")
                || args[0].equalsIgnoreCase("givetempcharm") || args[0].equalsIgnoreCase("givecapcharm")
                || args[0].equalsIgnoreCase("giveeventitem"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("goldenhour")) {
            return Arrays.asList("start", "stop");
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("giveitem"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            return Arrays.stream(EntityType.values())
                    .filter(EntityType::isAlive)
                    .map(EntityType::getName)
                    .filter(s -> s.startsWith(args[args.length - 1].toUpperCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
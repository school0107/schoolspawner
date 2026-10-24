package com.school.spawner;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            sender.sendMessage(TextUtil.color("&#FFFF55/ss reload &#AAAAAA- Reload config"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss list &#AAAAAA- Danh sách mob"));
            sender.sendMessage(TextUtil.color("&#FFFF55/ss help &#AAAAAA- Trợ giúp"));
            return true;
        }

        switch (args[0].toLowerCase()) {
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
            case "help" -> {
                sender.sendMessage(TextUtil.color(prefix + "&#55FFFF✦ SchoolSpawner Help ✦"));
                sender.sendMessage(TextUtil.color("&#FFFF55/ss give <player> <mob> [level]"));
                sender.sendMessage(TextUtil.color("&#FFFF55/ss giveitem <mob> [level]"));
                sender.sendMessage(TextUtil.color("&#FFFF55/ss reload"));
                sender.sendMessage(TextUtil.color("&#FFFF55/ss list"));
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
            default -> {
                sender.sendMessage(TextUtil.color(prefix + "&#FF5555Lệnh không hợp lệ. Dùng /ss help"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "giveitem", "reload", "list", "help")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("giveitem"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            return Arrays.stream(EntityType.values())
                    .filter(EntityType::isAlive)
                    .map(EntityType::name)
                    .filter(s -> s.startsWith(args[args.length - 1].toUpperCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

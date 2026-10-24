package com.school.spawner;

import org.bukkit.plugin.java.JavaPlugin;

public class SchoolSpawner extends JavaPlugin {

    private static SchoolSpawner instance;
    private SpawnerManager manager;
    private SpawnerGUI gui;
    private SchoolExpansion expansion;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.manager = new SpawnerManager(this);
        this.gui = new SpawnerGUI(this);

        getServer().getPluginManager().registerEvents(new SpawnerListener(this), this);
        SpawnerCommand cmd = new SpawnerCommand(this);
        getCommand("schoolspawner").setExecutor(cmd);
        getCommand("schoolspawner").setTabCompleter(cmd);

        manager.startTask();

        // Đăng ký PlaceholderAPI
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.expansion = new SchoolExpansion(this);
            this.expansion.register();
            getLogger().info("§aHooked into PlaceholderAPI!");
        }

        getLogger().info("§aSchoolSpawner enabled! Version " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.stopTask();
            manager.saveToDisk();
        }
        if (expansion != null) {
            expansion.unregister();
        }
        getLogger().info("§cSchoolSpawner disabled.");
    }

    public static SchoolSpawner getInstance() { return instance; }
    public SpawnerManager getManager() { return manager; }
    public SpawnerGUI getGui() { return gui; }

    public void reload() {
        reloadConfig();
        manager.reload();
    }
}
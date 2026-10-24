package com.school.spawner;

import org.bukkit.plugin.java.JavaPlugin;

public class SchoolSpawner extends JavaPlugin {

    private static SchoolSpawner instance;
    private SpawnerManager manager;
    private SpawnerGUI gui;

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

        // schedule task
        manager.startTask();

        getLogger().info("§aSchoolSpawner enabled! Version " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.saveAll();
        if (manager != null) manager.stopTask();
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

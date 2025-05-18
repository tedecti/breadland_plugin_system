package me.tedecti.config;

import me.tedecti.BreadlandPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final BreadlandPlugin plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;

    public ConfigManager(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
    }

    public void loadConfigs() {
        // Load main config
        plugin.saveDefaultConfig();
        configs.put("config", plugin.getConfig());
        
        // Load events config
        File eventsFile = new File(plugin.getDataFolder(), "events.yml");
        if (!eventsFile.exists()) {
            plugin.saveResource("events.yml", false);
        }
        FileConfiguration eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);
        configs.put("events.yml", eventsConfig);
        configFiles.put("events.yml", eventsFile);
    }

    public FileConfiguration getConfig(String name) {
        FileConfiguration config = configs.get(name);
        if (config == null) {
            // Try to load the config if it's not loaded
            File configFile = new File(plugin.getDataFolder(), name);
            if (configFile.exists()) {
                config = YamlConfiguration.loadConfiguration(configFile);
                configs.put(name, config);
                configFiles.put(name, configFile);
            }
        }
        return config;
    }

    public void saveConfig(String name) {
        try {
            FileConfiguration config = configs.get(name);
            File configFile = configFiles.get(name);
            if (config != null && configFile != null) {
                config.save(configFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + name);
            e.printStackTrace();
        }
    }

    public void reloadConfig(String name) {
        if (name.equals("config")) {
            plugin.reloadConfig();
            configs.put("config", plugin.getConfig());
        } else {
            File configFile = configFiles.get(name);
            if (configFile != null) {
                configs.put(name, YamlConfiguration.loadConfiguration(configFile));
            }
        }
    }
} 
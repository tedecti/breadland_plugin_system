package me.tedecti.config;

import me.tedecti.BreadlandPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        FileConfiguration eventsConfig = loadWithDefaults(eventsFile, "events.yml");
        configs.put("events.yml", eventsConfig);
        configFiles.put("events.yml", eventsFile);
    }

    public FileConfiguration getConfig(String name) {
        FileConfiguration config = configs.get(name);
        if (config == null) {
            // Try to load the config if it's not loaded
            File configFile = new File(plugin.getDataFolder(), name);
            if (configFile.exists()) {
                config = loadWithDefaults(configFile, name);
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
                configs.put(name, loadWithDefaults(configFile, name));
            }
        }
    }

    // Keys missing from the server's file (e.g. added in a newer version) are copied from the bundled resource
    private FileConfiguration loadWithDefaults(File file, String resourceName) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource(resourceName);
        if (defaults == null) {
            return config;
        }

        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(key) && !config.contains(key, true)) {
                config.set(key, bundled.get(key));
                changed = true;
            }
        }
        config.setDefaults(bundled);

        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not add new defaults to " + resourceName + ": " + e.getMessage());
            }
        }
        return config;
    }
} 
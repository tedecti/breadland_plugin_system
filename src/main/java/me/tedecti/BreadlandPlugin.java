package me.tedecti;

import me.tedecti.events.EventManager;
import me.tedecti.config.ConfigManager;
import me.tedecti.events.deathnote.DeathNoteManager;
import me.tedecti.events.deathnote.DeathNoteHandler;
import me.tedecti.events.deathnote.DeathNoteCommand;
import me.tedecti.managers.GodVanishManager;
import me.tedecti.commands.GodVanishCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BreadlandPlugin extends JavaPlugin {
    private static BreadlandPlugin instance;
    private EventManager eventManager;
    private ConfigManager configManager;
    private DeathNoteManager deathNoteManager;
    private GodVanishManager godVanishManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Load configurations before managers read them
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        // Initialize managers
        this.eventManager = new EventManager(this);
        this.deathNoteManager = new DeathNoteManager(this);
        this.godVanishManager = new GodVanishManager(this);
        
        // Register events
        this.eventManager.registerEvents();
        
        // Register Death Note handler
        this.eventManager.registerListener(new DeathNoteHandler(this, deathNoteManager));
        
        // Register commands
        DeathNoteCommand deathNoteCommand = new DeathNoteCommand(this, deathNoteManager);
        getCommand("deathnote").setExecutor(deathNoteCommand);
        getCommand("deathnote").setTabCompleter(deathNoteCommand);
        getCommand("godvanish").setExecutor(new GodVanishCommand(this, godVanishManager));
        
        getLogger().info("BreadlandPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (deathNoteManager != null) {
            deathNoteManager.shutdown();
        }
        getLogger().info("BreadlandPlugin has been disabled!");
    }

    public static BreadlandPlugin getInstance() {
        return instance;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DeathNoteManager getDeathNoteManager() {
        return deathNoteManager;
    }

    public GodVanishManager getGodVanishManager() {
        return godVanishManager;
    }
}

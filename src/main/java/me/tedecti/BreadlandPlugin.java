package me.tedecti;

import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.ProtocolLibrary;
import me.tedecti.events.EventManager;
import me.tedecti.config.ConfigManager;
import me.tedecti.events.deathnote.DeathNoteManager;
import me.tedecti.events.deathnote.DeathNoteHandler;
import me.tedecti.events.deathnote.DeathNoteCommand;
import me.tedecti.managers.GodVanishManager;
import me.tedecti.commands.GodVanishCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;

public class BreadlandPlugin extends JavaPlugin {
    private static BreadlandPlugin instance;
    private EventManager eventManager;
    private ConfigManager configManager;
    private DeathNoteManager deathNoteManager;
    private GodVanishManager godVanishManager;
    private ProtocolManager protocolManager;
    private final Set<String> pendingDeaths = new HashSet<>();

    @Override
    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.eventManager = new EventManager(this);
        this.deathNoteManager = new DeathNoteManager(this);
        this.godVanishManager = new GodVanishManager(this);
        
        // Load configurations
        this.configManager.loadConfigs();
        
        // Register events
        this.eventManager.registerEvents();
        
        // Register Death Note handler
        this.eventManager.registerListener(new DeathNoteHandler(this, deathNoteManager));
        
        // Register commands
        getCommand("deathnote").setExecutor(new DeathNoteCommand(this, deathNoteManager));
        getCommand("godvanish").setExecutor(new GodVanishCommand(this, godVanishManager));
        
        getLogger().info("BreadlandPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
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

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public void activateDeathNote(ItemStack book, String targetPlayer) {
        Player p = getServer().getPlayer(targetPlayer);
        if (p != null && p.isOnline()) {
            p.setLastDamageCause(new org.bukkit.event.entity.EntityDamageEvent(
                p,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC,
                1000
            ));
            p.setHealth(0);
        }
        pendingDeaths.add(targetPlayer);
    }

    public boolean isDeathNoteDeath(String playerName) {
        boolean result = pendingDeaths.remove(playerName);
        return result;
    }
} 
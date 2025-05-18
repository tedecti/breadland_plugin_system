package me.tedecti.events;

import me.tedecti.BreadlandPlugin;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

public class EventManager {
    private final BreadlandPlugin plugin;
    private final List<Listener> eventListeners;

    public EventManager(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.eventListeners = new ArrayList<>();
    }

    public void registerEvents() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        
        // Register all event listeners
        for (Listener listener : eventListeners) {
            pluginManager.registerEvents(listener, plugin);
        }
    }

    public void registerListener(Listener listener) {
        eventListeners.add(listener);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public void unregisterListener(Listener listener) {
        eventListeners.remove(listener);
        // Note: Bukkit doesn't provide a direct way to unregister listeners
        // You would need to implement your own listener management if needed
    }
} 
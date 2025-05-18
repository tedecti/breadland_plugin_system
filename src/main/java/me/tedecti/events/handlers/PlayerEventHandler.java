package me.tedecti.events.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventHandler implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Example event handling
        event.getPlayer().sendMessage("Welcome to the server!");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Example event handling
        event.setQuitMessage("Goodbye, " + event.getPlayer().getName() + "!");
    }
} 
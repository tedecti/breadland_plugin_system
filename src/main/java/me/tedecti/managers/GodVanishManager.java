package me.tedecti.managers;

import me.tedecti.BreadlandPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GodVanishManager implements Listener {
    private final BreadlandPlugin plugin;
    private final Map<UUID, UUID> vanishedPlayers; // Vanished Player UUID -> Visible To Player UUID
    private final Map<UUID, UUID> deathGodBindings; // Death God UUID -> Bound Player UUID

    public GodVanishManager(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = new HashMap<>();
        this.deathGodBindings = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setVanished(Player player, Player visibleTo) {
        vanishedPlayers.put(player.getUniqueId(), visibleTo.getUniqueId());
        deathGodBindings.put(player.getUniqueId(), visibleTo.getUniqueId());
        updateVisibility(player);
    }

    public void removeVanish(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        deathGodBindings.remove(player.getUniqueId());
        // Show player to everyone
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, player);
        }
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.containsKey(player.getUniqueId());
    }

    public boolean isDeathGod(Player player) {
        return deathGodBindings.containsKey(player.getUniqueId());
    }

    public Player getBoundPlayer(Player deathGod) {
        UUID boundPlayerUUID = deathGodBindings.get(deathGod.getUniqueId());
        return boundPlayerUUID != null ? Bukkit.getPlayer(boundPlayerUUID) : null;
    }

    public Player getDeathGodByBoundPlayer(Player boundPlayer) {
        UUID boundPlayerUUID = boundPlayer.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : deathGodBindings.entrySet()) {
            if (entry.getValue().equals(boundPlayerUUID)) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private void updateVisibility(Player vanishedPlayer) {
        UUID visibleToUUID = vanishedPlayers.get(vanishedPlayer.getUniqueId());
        if (visibleToUUID == null) return;

        Player visibleTo = Bukkit.getPlayer(visibleToUUID);
        if (visibleTo == null || !visibleTo.isOnline()) {
            removeVanish(vanishedPlayer);
            return;
        }

        // Hide from everyone except the target player
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(visibleToUUID)) {
                onlinePlayer.showPlayer(plugin, vanishedPlayer);
            } else {
                onlinePlayer.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        UUID joiningPlayerUUID = joiningPlayer.getUniqueId();

        // Update visibility for all vanished players for the joining player
        for (Map.Entry<UUID, UUID> entry : vanishedPlayers.entrySet()) {
            Player vanishedPlayer = Bukkit.getPlayer(entry.getKey());
            if (vanishedPlayer == null) continue;

            if (joiningPlayerUUID.equals(entry.getValue())) {
                joiningPlayer.showPlayer(plugin, vanishedPlayer);
            } else {
                joiningPlayer.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        
        // If the player who can see the vanished player quits, remove vanish
        for (Map.Entry<UUID, UUID> entry : vanishedPlayers.entrySet()) {
            if (entry.getValue().equals(quittingPlayer.getUniqueId())) {
                Player vanishedPlayer = Bukkit.getPlayer(entry.getKey());
                if (vanishedPlayer != null) {
                    removeVanish(vanishedPlayer);
                    vanishedPlayer.sendMessage("§cИгрок, который мог вас видеть, вышел с сервера. Режим GodVanish выключен!");
                }
            }
        }

        // If a vanished player quits, remove them from the list
        vanishedPlayers.remove(quittingPlayer.getUniqueId());
        deathGodBindings.remove(quittingPlayer.getUniqueId());
    }
} 
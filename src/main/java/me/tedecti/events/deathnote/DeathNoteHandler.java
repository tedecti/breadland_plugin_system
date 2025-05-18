package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class DeathNoteHandler implements Listener {
    private final BreadlandPlugin plugin;
    private final DeathNoteManager deathNoteManager;

    public DeathNoteHandler(BreadlandPlugin plugin, DeathNoteManager deathNoteManager) {
        this.plugin = plugin;
        this.deathNoteManager = deathNoteManager;
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        ItemStack book = event.getPlayer().getInventory().getItemInMainHand();
        if (!deathNoteManager.isDeathNote(book)) return;

        // Проверяем, не пытается ли игрок подписать книгу
        if (event.isSigning()) return;

        BookMeta newMeta = event.getNewBookMeta();
        List<String> pages = new ArrayList<>(newMeta.getPages());
        
        // Get the last written line from the last page
        String lastPage = pages.get(pages.size() - 1);
        String[] lines = lastPage.split("\n");
        String targetPlayer = lines[lines.length - 1].trim();

        // Check if player exists and is online
        Player target = plugin.getServer().getPlayer(targetPlayer);
        if (target == null || !target.isOnline()) {
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("player-not-found")));
            return;
        }

        // Activate the death note
        deathNoteManager.activateDeathNote(book, targetPlayer, event.getPlayer());

        // Add a new empty line for the next name
        pages.set(pages.size() - 1, lastPage + "\n");
        newMeta.setPages(pages);
        event.setNewBookMeta(newMeta);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !deathNoteManager.isDeathNote(item)) return;

        // Update book appearance based on player permissions
        deathNoteManager.updateBookAppearance(event.getPlayer(), item);
    }

    @EventHandler
    public void onDispenserActivate(BlockDispenseEvent event) {
        if (!(event.getBlock().getState() instanceof Dispenser)) return;
        
        ItemStack item = event.getItem();
        if (!deathNoteManager.isDeathNote(item)) return;

        String targetPlayer = deathNoteManager.getTargetPlayer(item);
        if (targetPlayer != null) {
            event.setCancelled(true);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (deathNoteManager.isDeathNoteDeath(event.getEntity().getName())) {
            String deathMessage = deathNoteManager.getMessage("death")
                .replace("%player%", event.getEntity().getName());
            event.setDeathMessage(ChatColor.translateAlternateColorCodes('&', deathMessage));
        }
    }
} 
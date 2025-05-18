package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DeathNoteCommand implements CommandExecutor {
    private final BreadlandPlugin plugin;
    private final DeathNoteManager deathNoteManager;

    public DeathNoteCommand(BreadlandPlugin plugin, DeathNoteManager deathNoteManager) {
        this.plugin = plugin;
        this.deathNoteManager = deathNoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("player-only")));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("breadland.deathnote.create")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("usage")));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("player-not-found")));
            return true;
        }

        ItemStack deathNote = deathNoteManager.createDeathNote(target);
        target.getInventory().addItem(deathNote);
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', deathNoteManager.getMessage("received")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            deathNoteManager.getMessage("given").replace("%player%", targetName)));

        return true;
    }
} 
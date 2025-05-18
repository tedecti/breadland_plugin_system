package me.tedecti.commands;

import me.tedecti.BreadlandPlugin;
import me.tedecti.managers.GodVanishManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GodVanishCommand implements CommandExecutor {
    private final BreadlandPlugin plugin;
    private final GodVanishManager godVanishManager;

    public GodVanishCommand(BreadlandPlugin plugin, GodVanishManager godVanishManager) {
        this.plugin = plugin;
        this.godVanishManager = godVanishManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда может быть использована только игроком!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("breadland.godvanish")) {
            player.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Использование: /godvanish <игрок>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return true;
        }

        if (godVanishManager.isVanished(player)) {
            godVanishManager.removeVanish(player);
            player.sendMessage(ChatColor.GREEN + "Режим GodVanish выключен!");
        } else {
            godVanishManager.setVanished(player, target);
            player.sendMessage(ChatColor.GREEN + "Режим GodVanish включен! Вас видит только " + target.getName());
        }

        return true;
    }
} 
package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /deathnote give|revoke|eyes|list|dummy|reload. "/deathnote <player>" still works as "give".
 */
public class DeathNoteCommand implements TabExecutor {
    private static final String ADMIN = "breadland.deathnote.admin";
    private static final List<String> SUBCOMMANDS = List.of("give", "revoke", "eyes", "list", "dummy", "reload");
    private static final List<String> DUMMY_ACTIONS = List.of("remove", "list", "clear");

    private final BreadlandPlugin plugin;
    private final DeathNoteManager deathNoteManager;

    public DeathNoteCommand(BreadlandPlugin plugin, DeathNoteManager deathNoteManager) {
        this.plugin = plugin;
        this.deathNoteManager = deathNoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            deathNoteManager.sendMessage(sender, "usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args.length > 1 ? args[1] : null);
            case "revoke" -> revoke(sender, args);
            case "eyes" -> eyes(sender, args);
            case "list" -> list(sender);
            case "dummy" -> dummy(sender, args);
            case "reload" -> {
                if (!checkAdmin(sender)) return true;
                deathNoteManager.reloadConfig();
                deathNoteManager.sendMessage(sender, "reloaded");
            }
            default -> give(sender, args[0]);
        }
        return true;
    }

    private void give(CommandSender sender, String targetName) {
        if (!sender.hasPermission("breadland.deathnote.create")) {
            deathNoteManager.sendMessage(sender, "no-permission");
            return;
        }
        if (targetName == null) {
            deathNoteManager.sendMessage(sender, "usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            deathNoteManager.sendMessage(sender, "player-not-found");
            return;
        }

        if (deathNoteManager.giveNote(sender, target)) {
            deathNoteManager.sendMessage(sender, "given", "%player%", target.getName());
        }
    }

    private void revoke(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            deathNoteManager.sendMessage(sender, "usage");
            return;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null || deathNoteManager.getStorage().getActiveNoteOf(target.getUniqueId()) == null) {
            deathNoteManager.sendMessage(sender, "not-owner", "%player%", args[1]);
            return;
        }

        deathNoteManager.loseOwnership(target.getUniqueId());
        deathNoteManager.logAction(sender.getName() + " revoked the Death Note of " + target.getName());
        deathNoteManager.sendMessage(sender, "revoked", "%player%", target.getName());
        if (target.getPlayer() != null) {
            deathNoteManager.sendMessage(target.getPlayer(), "revoked-target");
        }
    }

    private void eyes(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            deathNoteManager.sendMessage(sender, "usage");
            return;
        }

        if (args[1].equalsIgnoreCase("revoke")) {
            OfflinePlayer target = args.length > 2 ? findPlayer(args[2]) : null;
            if (target == null || !deathNoteManager.getEyes().hasEyes(target.getUniqueId())) {
                deathNoteManager.sendMessage(sender, "eyes-none", "%player%", args.length > 2 ? args[2] : "?");
                return;
            }
            deathNoteManager.getEyes().revoke(target.getUniqueId());
            deathNoteManager.getStorage().save();
            deathNoteManager.sendMessage(sender, "eyes-revoked", "%player%", target.getName());
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            deathNoteManager.sendMessage(sender, "player-not-found");
            return;
        }
        deathNoteManager.offerEyes(sender, target);
    }

    private void list(CommandSender sender) {
        if (!checkAdmin(sender)) return;

        List<DeathEntry> pending = deathNoteManager.getPendingEntries();
        if (pending.isEmpty()) {
            deathNoteManager.sendMessage(sender, "list-empty");
            return;
        }
        deathNoteManager.sendMessage(sender, "list-header", "%count%", String.valueOf(pending.size()));
        for (DeathEntry entry : pending) {
            deathNoteManager.sendMessage(sender, "list-entry",
                "%player%", entry.getTargetName(),
                "%writer%", entry.getWriterName(),
                "%time%", TimeFormat.formatClock(entry.getSecondsLeft()));
        }
    }

    private void dummy(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        TestDummies dummies = deathNoteManager.getDummies();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";

        switch (action) {
            case "clear" -> deathNoteManager.sendMessage(sender, "dummy-cleared", "%count%", String.valueOf(dummies.clear()));
            case "list" -> {
                List<String> names = dummies.getNames();
                deathNoteManager.sendMessage(sender, "dummy-list",
                    "%names%", names.isEmpty() ? "-" : String.join(", ", names));
            }
            case "remove" -> {
                if (args.length < 3 || !dummies.remove(args[2])) {
                    deathNoteManager.sendMessage(sender, "dummy-not-found", "%name%", args.length < 3 ? "?" : args[2]);
                    return;
                }
                deathNoteManager.sendMessage(sender, "dummy-removed", "%name%", args[2]);
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    deathNoteManager.sendMessage(sender, "player-only");
                    return;
                }

                String name = action.isEmpty() ? nextDummyName(dummies) : args[1];
                if (!name.matches("[A-Za-z0-9_]{3,16}")) {
                    deathNoteManager.sendMessage(sender, "dummy-bad-name");
                    return;
                }
                // Имя куклы не должно совпадать с ником игрока: тетрадь сначала ищет игроков
                if (findPlayer(name) != null) {
                    deathNoteManager.sendMessage(sender, "dummy-name-taken", "%name%", name);
                    return;
                }

                dummies.spawn(player, name);
                deathNoteManager.sendMessage(sender, "dummy-spawned", "%name%", name);
            }
        }
    }

    private String nextDummyName(TestDummies dummies) {
        int number = 1;
        while (dummies.find("Dummy" + number) != null) number++;
        return "Dummy" + number;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        deathNoteManager.sendMessage(sender, "no-permission");
        return false;
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("eyes")) {
            options.add("revoke");
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("revoke"))) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("eyes") && args[1].equalsIgnoreCase("revoke")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dummy")) {
            options.addAll(DUMMY_ACTIONS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dummy") && args[1].equalsIgnoreCase("remove")) {
            options.addAll(deathNoteManager.getDummies().getNames());
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}

package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shinigami eyes: granted by an admin through a deal. While the holder owns a Death Note,
 * half of their max health is taken; in return they see every player's real name and,
 * for players already written in a note, the time they have left.
 */
public class ShinigamiEyes {
    private static final double LABEL_HEIGHT = 2.45;
    private static final double LABEL_RANGE = 48;

    private final BreadlandPlugin plugin;
    private final DeathNoteStorage storage;
    private final NamespacedKey healthKey;
    private final Map<UUID, TextDisplay> labels = new HashMap<>(); // Labelled player -> label entity
    private BukkitTask task;

    public ShinigamiEyes(BreadlandPlugin plugin, DeathNoteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.healthKey = new NamespacedKey(plugin, "shinigami_eyes");
    }

    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateLabels, 2L, 2L);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refreshHealth(player);
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        labels.values().forEach(TextDisplay::remove);
        labels.clear();
    }

    public boolean hasEyes(UUID player) {
        return storage.getEyes().contains(player);
    }

    /** Eyes only work (and only cost health) while the holder owns an active note. */
    public boolean isActive(Player player) {
        return hasEyes(player.getUniqueId()) && storage.getActiveNoteOf(player.getUniqueId()) != null;
    }

    public void grant(Player player) {
        storage.getEyes().add(player.getUniqueId());
        storage.markDirty();
        refreshHealth(player);
    }

    /** Removes the eyes and gives the health back. */
    public void revoke(UUID player) {
        if (storage.getEyes().remove(player)) storage.markDirty();
        Player online = plugin.getServer().getPlayer(player);
        if (online != null) refreshHealth(online);
    }

    /** Applies or removes the half-health modifier to match the player's current state. */
    public void refreshHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        boolean shouldHave = isActive(player);
        AttributeModifier current = maxHealth.getModifier(healthKey);
        if (shouldHave && current == null) {
            maxHealth.addModifier(new AttributeModifier(healthKey, -0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            if (!player.isDead() && player.getHealth() > maxHealth.getValue()) {
                player.setHealth(maxHealth.getValue());
            }
        } else if (!shouldHave && current != null) {
            maxHealth.removeModifier(healthKey);
        }
    }

    private void updateLabels() {
        List<Player> holders = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isActive(player) && !player.isDead()) holders.add(player);
        }

        if (holders.isEmpty()) {
            if (!labels.isEmpty()) {
                labels.values().forEach(TextDisplay::remove);
                labels.clear();
            }
            return;
        }

        // Убираем подписи ушедших игроков
        Iterator<Map.Entry<UUID, TextDisplay>> iterator = labels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TextDisplay> entry = iterator.next();
            Player target = plugin.getServer().getPlayer(entry.getKey());
            if (target == null || target.isDead() || !entry.getValue().isValid()) {
                entry.getValue().remove();
                iterator.remove();
            }
        }

        Map<UUID, DeathEntry> pending = new HashMap<>();
        for (DeathEntry entry : storage.getPendingEntries()) {
            pending.put(entry.getTarget(), entry);
        }

        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.isDead() || !isNearAnyHolder(target, holders)) {
                TextDisplay label = labels.remove(target.getUniqueId());
                if (label != null) label.remove();
                continue;
            }

            Location position = target.getLocation().add(0, LABEL_HEIGHT, 0);
            TextDisplay label = labels.get(target.getUniqueId());
            if (label == null || !label.getWorld().equals(target.getWorld())) {
                if (label != null) label.remove();
                label = spawnLabel(position);
                labels.put(target.getUniqueId(), label);
            } else {
                // Не сажаем подпись на игрока как пассажира: с пассажиром плагины не могут его телепортировать
                label.teleport(position);
            }

            label.text(buildLabelText(target, pending.get(target.getUniqueId())));

            // Проходим по всем, а не только по обладателям: тот, кто потерял глаза, должен перестать видеть подписи
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                boolean visible = viewer != target && holders.contains(viewer)
                    && viewer.getWorld().equals(target.getWorld()) && viewer.canSee(target);
                if (visible) {
                    viewer.showEntity(plugin, label);
                } else {
                    viewer.hideEntity(plugin, label);
                }
            }
        }
    }

    private boolean isNearAnyHolder(Player target, List<Player> holders) {
        for (Player holder : holders) {
            if (holder != target && holder.getWorld().equals(target.getWorld())
                && holder.getLocation().distanceSquared(target.getLocation()) <= LABEL_RANGE * LABEL_RANGE) {
                return true;
            }
        }
        return false;
    }

    private TextDisplay spawnLabel(Location position) {
        return position.getWorld().spawn(position, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setTeleportDuration(2);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
        });
    }

    private Component buildLabelText(Player target, DeathEntry entry) {
        Component text = Component.text(target.getName(), NamedTextColor.RED, TextDecoration.BOLD);
        if (entry != null) {
            text = text.appendNewline()
                .append(Component.text("† " + TimeFormat.formatClock(entry.getSecondsLeft()), NamedTextColor.DARK_RED));
        }
        return text;
    }

    public void removeLabel(UUID player) {
        TextDisplay label = labels.remove(player);
        if (label != null) label.remove();
    }
}

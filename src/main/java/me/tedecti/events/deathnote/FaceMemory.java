package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The "you must know the victim's face" rule. Periodically checks who each note owner can see
 * and remembers it, so the owner can later write those names.
 */
public class FaceMemory {
    public enum Mode { NONE, RADIUS, SEEN, FACE }

    private final BreadlandPlugin plugin;
    private final DeathNoteStorage storage;
    private final TestDummies dummies;
    private BukkitTask task;

    private Mode mode = Mode.FACE;
    private double radius;
    private double viewDistance;
    private double fovCos;
    private double faceCos;
    private long memoryMillis;
    private boolean invisibilityHides;
    private final Set<Material> concealingHelmets = new HashSet<>();

    public FaceMemory(BreadlandPlugin plugin, DeathNoteStorage storage, TestDummies dummies) {
        this.plugin = plugin;
        this.storage = storage;
        this.dummies = dummies;
    }

    public void load(ConfigurationSection section) {
        if (task != null) task.cancel();
        concealingHelmets.clear();

        try {
            mode = Mode.valueOf(section.getString("mode", "face").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown death-note.know-face.mode, using 'face'");
            mode = Mode.FACE;
        }
        radius = section.getDouble("radius", 1000);
        viewDistance = section.getDouble("view-distance", 48);
        fovCos = Math.cos(Math.toRadians(section.getDouble("fov", 140) / 2));
        faceCos = Math.cos(Math.toRadians(section.getDouble("face-angle", 120) / 2));
        memoryMillis = section.getLong("memory-minutes", 1440) * 60_000L;
        invisibilityHides = section.getBoolean("invisibility-hides-face", true);
        for (String name : section.getStringList("concealing-helmets")) {
            Material material = Material.matchMaterial(name);
            if (material != null) concealingHelmets.add(material);
        }

        if (mode == Mode.SEEN || mode == Mode.FACE) {
            long interval = Math.max(1, section.getLong("check-interval-ticks", 10));
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, interval, interval);
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    public Mode getMode() {
        return mode;
    }

    public double getRadius() {
        return radius;
    }

    public boolean usesMemory() {
        return mode == Mode.SEEN || mode == Mode.FACE;
    }

    private void scan() {
        long now = System.currentTimeMillis();
        for (Player observer : plugin.getServer().getOnlinePlayers()) {
            // Помнить лица нужно только тем, кто может писать в тетрадь
            if (storage.getActiveNoteOf(observer.getUniqueId()) == null) continue;
            if (observer.isDead() || observer.getGameMode() == GameMode.SPECTATOR) continue;

            List<LivingEntity> targets = new ArrayList<>(observer.getWorld().getPlayers());
            for (LivingEntity dummy : dummies.getAll()) {
                if (dummy.getWorld().equals(observer.getWorld())) targets.add(dummy);
            }

            for (LivingEntity target : targets) {
                if (target == observer || !observer.canSee(target)) continue;
                if (sees(observer, target)) {
                    storage.getFaceMemory(observer.getUniqueId()).put(target.getUniqueId(), now);
                    storage.markDirty();
                }
            }
        }
    }

    private boolean sees(Player observer, LivingEntity target) {
        Location eye = observer.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        double distanceSquared = eye.distanceSquared(targetEye);
        if (distanceSquared > viewDistance * viewDistance || distanceSquared < 0.01) return false;

        Vector toTarget = targetEye.toVector().subtract(eye.toVector()).normalize();
        if (eye.getDirection().dot(toTarget) < fovCos) return false;

        if (mode == Mode.FACE) {
            if (isFaceHidden(target)) return false;
            // Жертва должна смотреть в сторону наблюдателя, иначе лица не видно
            if (targetEye.getDirection().dot(toTarget.clone().multiply(-1)) < faceCos) return false;
        }

        return observer.hasLineOfSight(target);
    }

    private boolean isFaceHidden(LivingEntity entity) {
        // Голова на кукле — это её лицо, а не маска
        if (!(entity instanceof Player target)) return false;
        if (invisibilityHides && target.hasPotionEffect(PotionEffectType.INVISIBILITY)) return true;
        ItemStack helmet = target.getInventory().getHelmet();
        return helmet != null && concealingHelmets.contains(helmet.getType());
    }

    /** Whether the writer knows the target well enough to kill them. */
    public boolean knows(Player writer, OfflinePlayer target) {
        Player online = target.getPlayer();
        return knows(writer, target.getUniqueId(), online == null ? null : online.getLocation());
    }

    /**
     * @param location where the target is now, or null if they are not in the world
     */
    public boolean knows(Player writer, UUID targetId, Location location) {
        switch (mode) {
            case NONE:
                return true;
            case RADIUS: {
                if (location == null) return false;
                if (radius <= 0) return true;
                return writer.getWorld().equals(location.getWorld())
                    && location.distanceSquared(writer.getLocation()) <= radius * radius;
            }
            default: {
                Long seenAt = storage.getFaceMemory(writer.getUniqueId()).get(targetId);
                if (seenAt == null) return false;
                return memoryMillis <= 0 || System.currentTimeMillis() - seenAt <= memoryMillis;
            }
        }
    }

    /** Names of everyone the writer currently remembers, most recently seen first. */
    public List<String> getKnownNames(Player writer, int limit) {
        List<String> names = new ArrayList<>();
        if (!usesMemory()) return names;

        long now = System.currentTimeMillis();
        storage.getFaceMemory(writer.getUniqueId()).entrySet().stream()
            .filter(entry -> memoryMillis <= 0 || now - entry.getValue() <= memoryMillis)
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(limit)
            .forEach(entry -> {
                String name = dummies.getName(entry.getKey());
                if (name == null) name = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
                if (name != null) names.add(name);
            });
        return names;
    }

    public void forget(UUID observer) {
        storage.clearFaceMemory(observer);
    }
}

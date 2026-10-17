package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Test dummies for the Death Note: armor stands that can be written into the note like players.
 * They are not saved with the world and disappear on restart.
 */
public class TestDummies {
    private final BreadlandPlugin plugin;
    private final Map<String, UUID> dummies = new LinkedHashMap<>(); // Lowercase name -> entity UUID
    private final Map<UUID, String> names = new LinkedHashMap<>(); // Entity UUID -> name as written

    public TestDummies(BreadlandPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawns a dummy two blocks in front of the creator, facing them. Replaces a dummy with the same name. */
    public ArmorStand spawn(Player creator, String name) {
        remove(name);

        Location eye = creator.getLocation();
        Location location = eye.clone().add(eye.getDirection().setY(0).normalize().multiply(2));
        location.setYaw(eye.getYaw() + 180);
        location.setPitch(0);

        ArmorStand dummy = creator.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);
            stand.customName(Component.text("[Кукла] ", NamedTextColor.GRAY).append(Component.text(name, NamedTextColor.WHITE)));
            stand.setCustomNameVisible(true);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(creator);
                head.setItemMeta(skull);
            }
            EntityEquipment equipment = stand.getEquipment();
            equipment.setHelmet(head);
            equipment.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        });

        dummies.put(name.toLowerCase(Locale.ROOT), dummy.getUniqueId());
        names.put(dummy.getUniqueId(), name);
        return dummy;
    }

    public LivingEntity find(String name) {
        UUID id = dummies.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : get(id);
    }

    public LivingEntity get(UUID id) {
        Entity entity = plugin.getServer().getEntity(id);
        if (entity instanceof LivingEntity living && entity.isValid()) return living;
        return null;
    }

    public String getName(UUID id) {
        return names.get(id);
    }

    public boolean isDummy(Entity entity) {
        return names.containsKey(entity.getUniqueId());
    }

    public List<LivingEntity> getAll() {
        List<LivingEntity> alive = new ArrayList<>();
        for (UUID id : names.keySet()) {
            LivingEntity dummy = get(id);
            if (dummy != null) alive.add(dummy);
        }
        return alive;
    }

    public List<String> getNames() {
        return new ArrayList<>(names.values());
    }

    /** Kills a dummy by the note: it crumbles to dust. */
    public void kill(UUID id) {
        LivingEntity dummy = get(id);
        if (dummy != null) {
            dummy.getWorld().spawnParticle(Particle.SMOKE, dummy.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
            dummy.remove();
        }
        forget(id);
    }

    public boolean remove(String name) {
        UUID id = dummies.get(name.toLowerCase(Locale.ROOT));
        if (id == null) return false;
        LivingEntity dummy = get(id);
        if (dummy != null) dummy.remove();
        forget(id);
        return true;
    }

    public int clear() {
        int count = names.size();
        for (UUID id : new ArrayList<>(names.keySet())) {
            LivingEntity dummy = get(id);
            if (dummy != null) dummy.remove();
        }
        dummies.clear();
        names.clear();
        return count;
    }

    private void forget(UUID id) {
        String name = names.remove(id);
        if (name != null) dummies.remove(name.toLowerCase(Locale.ROOT));
    }
}

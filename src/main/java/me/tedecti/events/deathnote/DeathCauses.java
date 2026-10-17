package me.tedecti.events.deathnote;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Causes of death that can be written into a note, loaded from events.yml.
 * A cause the note can't perform falls back to the default one (heart attack), as in the rules.
 */
public class DeathCauses {
    public enum Effect { NONE, FIRE, LIGHTNING, EXPLOSION, DROWN, FALL, FREEZE }

    public record Cause(String id, String name, List<String> aliases, Effect effect, String deathMessage) {
        public void playEffect(LivingEntity victim) {
            Location location = victim.getLocation();
            World world = victim.getWorld();
            Location body = location.clone().add(0, 1, 0);
            switch (effect) {
                case FIRE -> {
                    victim.setFireTicks(100);
                    world.spawnParticle(Particle.FLAME, body, 40, 0.3, 0.6, 0.3, 0.02);
                    world.playSound(location, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
                }
                case LIGHTNING -> world.strikeLightningEffect(location);
                case EXPLOSION -> {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, body, 1);
                    world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                }
                case DROWN -> {
                    world.spawnParticle(Particle.BUBBLE_COLUMN_UP, body, 60, 0.3, 0.6, 0.3, 0.1);
                    world.playSound(location, Sound.ENTITY_PLAYER_HURT_DROWN, 1f, 1f);
                }
                case FALL -> world.playSound(location, Sound.ENTITY_PLAYER_BIG_FALL, 1f, 1f);
                case FREEZE -> {
                    world.spawnParticle(Particle.SNOWFLAKE, body, 60, 0.3, 0.6, 0.3, 0.02);
                    world.playSound(location, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
                }
                case NONE -> world.playSound(location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
            }
        }
    }

    private final Map<String, Cause> causes = new LinkedHashMap<>();
    private String defaultCauseId = "heart-attack";

    public void load(ConfigurationSection section, String defaultCauseId) {
        causes.clear();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection causeSection = section.getConfigurationSection(id);
                if (causeSection == null) continue;

                Effect effect;
                try {
                    effect = Effect.valueOf(causeSection.getString("effect", "none").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    effect = Effect.NONE;
                }

                List<String> aliases = new ArrayList<>();
                for (String alias : causeSection.getStringList("aliases")) {
                    aliases.add(normalize(alias));
                }

                causes.put(id, new Cause(id,
                    causeSection.getString("name", id),
                    aliases,
                    effect,
                    causeSection.getString("death-message", "&4%player% &cумер.")));
            }
        }

        if (!causes.containsKey(defaultCauseId)) {
            causes.putIfAbsent("heart-attack", new Cause("heart-attack", "Сердечный приступ", List.of(), Effect.NONE,
                "&4%player% &cумер от сердечного приступа."));
            defaultCauseId = "heart-attack";
        }
        this.defaultCauseId = defaultCauseId;
    }

    public Cause getDefault() {
        return causes.get(defaultCauseId);
    }

    public Cause get(String id) {
        Cause cause = id == null ? null : causes.get(id);
        return cause != null ? cause : getDefault();
    }

    /** Finds the cause whose alias appears in the written text, or null if none matches. */
    public Cause match(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return null;
        for (Cause cause : causes.values()) {
            for (String alias : cause.aliases()) {
                if (!alias.isEmpty() && normalized.contains(alias)) return cause;
            }
        }
        return null;
    }

    public List<Cause> getAll() {
        return new ArrayList<>(causes.values());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }
}

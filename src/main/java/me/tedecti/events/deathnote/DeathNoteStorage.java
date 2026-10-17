package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists notes, entries, shinigami eyes and face memory to deathnote-data.yml,
 * so countdowns, cooldowns and ownership survive restarts.
 */
public class DeathNoteStorage {
    private final BreadlandPlugin plugin;
    private final File file;
    private final Map<UUID, NoteRecord> notes = new LinkedHashMap<>();
    private final Set<UUID> eyes = new HashSet<>();
    private final Map<UUID, Map<UUID, Long>> faces = new HashMap<>(); // Observer -> (Target -> last seen millis)
    private boolean dirty;

    public DeathNoteStorage(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "deathnote-data.yml");
    }

    public void load() {
        notes.clear();
        eyes.clear();
        faces.clear();
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection notesSection = data.getConfigurationSection("notes");
        if (notesSection != null) {
            for (String bookKey : notesSection.getKeys(false)) {
                ConfigurationSection section = notesSection.getConfigurationSection(bookKey);
                UUID bookId = parseUuid(bookKey);
                UUID owner = section == null ? null : parseUuid(section.getString("owner"));
                if (bookId == null || owner == null) continue;

                NoteRecord note = new NoteRecord(bookId, owner, section.getBoolean("active", true), section.getLong("cooldown-until"));
                for (Map<?, ?> raw : section.getMapList("entries")) {
                    DeathEntry entry = readEntry(bookId, raw);
                    if (entry != null) note.getEntries().add(entry);
                }
                notes.put(bookId, note);
            }
        }

        for (String value : data.getStringList("eyes")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) eyes.add(uuid);
        }

        ConfigurationSection facesSection = data.getConfigurationSection("faces");
        if (facesSection != null) {
            for (String observerKey : facesSection.getKeys(false)) {
                UUID observer = parseUuid(observerKey);
                ConfigurationSection seen = facesSection.getConfigurationSection(observerKey);
                if (observer == null || seen == null) continue;
                Map<UUID, Long> memory = new HashMap<>();
                for (String targetKey : seen.getKeys(false)) {
                    UUID target = parseUuid(targetKey);
                    if (target != null) memory.put(target, seen.getLong(targetKey));
                }
                faces.put(observer, memory);
            }
        }
    }

    private DeathEntry readEntry(UUID bookId, Map<?, ?> raw) {
        UUID id = parseUuid(asString(raw.get("id")));
        UUID target = parseUuid(asString(raw.get("target")));
        UUID writer = parseUuid(asString(raw.get("writer")));
        if (id == null || target == null || writer == null) return null;

        DeathEntry.Status status;
        try {
            status = DeathEntry.Status.valueOf(asString(raw.get("status")));
        } catch (IllegalArgumentException | NullPointerException e) {
            status = DeathEntry.Status.CANCELLED;
        }

        return new DeathEntry(id, bookId, target, asString(raw.get("target-name")), writer, asString(raw.get("writer-name")),
            asString(raw.get("cause")), asString(raw.get("cause-text")), asLong(raw.get("written-at")), asLong(raw.get("death-at")), Boolean.TRUE.equals(raw.get("dummy")), status);
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();

        for (NoteRecord note : notes.values()) {
            String path = "notes." + note.getBookId();
            data.set(path + ".owner", note.getOwner().toString());
            data.set(path + ".active", note.isActive());
            data.set(path + ".cooldown-until", note.getCooldownUntil());

            List<Map<String, Object>> entries = new ArrayList<>();
            for (DeathEntry entry : note.getEntries()) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("id", entry.getId().toString());
                raw.put("target", entry.getTarget().toString());
                raw.put("target-name", entry.getTargetName());
                raw.put("writer", entry.getWriter().toString());
                raw.put("writer-name", entry.getWriterName());
                raw.put("cause", entry.getCauseId());
                raw.put("cause-text", entry.getCauseText());
                raw.put("written-at", entry.getWrittenAt());
                raw.put("death-at", entry.getDeathAt());
                if (entry.isDummy()) raw.put("dummy", true);
                raw.put("status", entry.getStatus().name());
                entries.add(raw);
            }
            data.set(path + ".entries", entries);
        }

        data.set("eyes", eyes.stream().map(UUID::toString).toList());

        faces.forEach((observer, memory) ->
            memory.forEach((target, seenAt) -> data.set("faces." + observer + "." + target, seenAt)));

        try {
            file.getParentFile().mkdirs();
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save deathnote-data.yml: " + e.getMessage());
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public NoteRecord getNote(UUID bookId) {
        return notes.get(bookId);
    }

    public void addNote(NoteRecord note) {
        notes.put(note.getBookId(), note);
        markDirty();
    }

    public Collection<NoteRecord> getNotes() {
        return notes.values();
    }

    public NoteRecord getActiveNoteOf(UUID owner) {
        for (NoteRecord note : notes.values()) {
            if (note.isActive() && note.getOwner().equals(owner)) return note;
        }
        return null;
    }

    public List<DeathEntry> getPendingEntries() {
        List<DeathEntry> pending = new ArrayList<>();
        for (NoteRecord note : notes.values()) {
            for (DeathEntry entry : note.getEntries()) {
                if (entry.isPending()) pending.add(entry);
            }
        }
        return pending;
    }

    public DeathEntry getPendingEntryFor(UUID target) {
        for (DeathEntry entry : getPendingEntries()) {
            if (entry.getTarget().equals(target)) return entry;
        }
        return null;
    }

    public Set<UUID> getEyes() {
        return eyes;
    }

    public Map<UUID, Long> getFaceMemory(UUID observer) {
        return faces.computeIfAbsent(observer, key -> new HashMap<>());
    }

    public void clearFaceMemory(UUID observer) {
        if (faces.remove(observer) != null) markDirty();
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? 0 : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

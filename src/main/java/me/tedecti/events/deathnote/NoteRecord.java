package me.tedecti.events.deathnote;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side state of one Death Note item. The item itself only carries the book ID;
 * everything written in it lives here, so it can't be erased or edited by the client.
 */
public class NoteRecord {
    private final UUID bookId;
    private final UUID owner;
    private final List<DeathEntry> entries = new ArrayList<>();
    private boolean active;
    private long cooldownUntil;

    public NoteRecord(UUID bookId, UUID owner, boolean active, long cooldownUntil) {
        this.bookId = bookId;
        this.owner = owner;
        this.active = active;
        this.cooldownUntil = cooldownUntil;
    }

    public UUID getBookId() { return bookId; }
    public UUID getOwner() { return owner; }
    public List<DeathEntry> getEntries() { return entries; }
    public boolean isActive() { return active; }
    public long getCooldownUntil() { return cooldownUntil; }

    public void setActive(boolean active) { this.active = active; }
    public void setCooldownUntil(long cooldownUntil) { this.cooldownUntil = cooldownUntil; }

    public long getCooldownSecondsLeft() {
        return Math.max(0, (cooldownUntil - System.currentTimeMillis() + 999) / 1000);
    }
}

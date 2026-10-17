package me.tedecti.events.deathnote;

import java.util.UUID;

/**
 * One name written into a Death Note. Entries are never removed from a note:
 * once written, they stay in the book (and in storage) forever.
 */
public class DeathEntry {
    public enum Status { PENDING, DEAD, CANCELLED }

    private final UUID id;
    private final UUID bookId;
    private final UUID target;
    private final String targetName;
    private final UUID writer;
    private final String writerName;
    private final String causeId;
    private final String causeText; // What the writer actually typed, shown in the book
    private final long writtenAt;
    private final long deathAt;
    private final boolean dummy; // Target is a test dummy, not a player
    private Status status;

    public DeathEntry(UUID id, UUID bookId, UUID target, String targetName, UUID writer, String writerName,
                      String causeId, String causeText, long writtenAt, long deathAt, boolean dummy, Status status) {
        this.id = id;
        this.bookId = bookId;
        this.target = target;
        this.targetName = targetName;
        this.writer = writer;
        this.writerName = writerName;
        this.causeId = causeId;
        this.causeText = causeText;
        this.writtenAt = writtenAt;
        this.deathAt = deathAt;
        this.dummy = dummy;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getBookId() { return bookId; }
    public UUID getTarget() { return target; }
    public String getTargetName() { return targetName; }
    public UUID getWriter() { return writer; }
    public String getWriterName() { return writerName; }
    public String getCauseId() { return causeId; }
    public String getCauseText() { return causeText; }
    public long getWrittenAt() { return writtenAt; }
    public long getDeathAt() { return deathAt; }
    public boolean isDummy() { return dummy; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }

    public boolean isPending() { return status == Status.PENDING; }

    public long getSecondsLeft() {
        return Math.max(0, (deathAt - System.currentTimeMillis() + 999) / 1000);
    }
}

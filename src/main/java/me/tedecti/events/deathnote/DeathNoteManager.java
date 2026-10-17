package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import me.tedecti.managers.GodVanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathNoteManager implements Listener {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BreadlandPlugin plugin;
    private final DeathNoteStorage storage;
    private final DeathCauses causes;
    private final FaceMemory faceMemory;
    private final ShinigamiEyes eyes;
    private final DeathNoteBook book;
    private final Map<UUID, String> dyingFromNote = new HashMap<>(); // Victim -> cause ID (for the death message)
    private final NamespacedKey deathNoteKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey ruleBookKey;
    private FileConfiguration config;
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public DeathNoteManager(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.storage = new DeathNoteStorage(plugin);
        this.causes = new DeathCauses();
        this.faceMemory = new FaceMemory(plugin, storage);
        this.eyes = new ShinigamiEyes(plugin, storage);
        this.book = new DeathNoteBook(plugin, causes);
        this.deathNoteKey = new NamespacedKey(plugin, "death_note");
        this.ownerKey = new NamespacedKey(plugin, "death_note_owner");
        this.ruleBookKey = new NamespacedKey(plugin, "death_note_rules");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        storage.load();
        reloadConfig();

        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, storage::saveIfDirty, 20L * 60, 20L * 60);
        eyes.start();
    }

    public void reloadConfig() {
        plugin.getConfigManager().reloadConfig("events.yml");
        this.config = plugin.getConfigManager().getConfig("events.yml");
        if (this.config == null) {
            plugin.getLogger().warning("Failed to load events.yml configuration!");
            this.config = new YamlConfiguration();
        }

        causes.load(config.getConfigurationSection("death-note.causes"), config.getString("death-note.default-cause", "heart-attack"));
        ConfigurationSection knowFace = config.getConfigurationSection("death-note.know-face");
        faceMemory.load(knowFace != null ? knowFace : new YamlConfiguration());
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        faceMemory.shutdown();
        eyes.shutdown();
        storage.save();
    }

    public DeathNoteStorage getStorage() {
        return storage;
    }

    public ShinigamiEyes getEyes() {
        return eyes;
    }

    private long getDefaultDelay() {
        return config.getLong("death-note.death-delay", 40);
    }

    private long getMinDelay() {
        return config.getLong("death-note.min-delay", 10);
    }

    private long getMaxDelay() {
        return config.getLong("death-note.max-delay", 3600);
    }

    private long getCooldownMillis() {
        return config.getLong("death-note.cooldown", 300) * 1000L;
    }

    // ---------------------------------------------------------------- Items

    private ItemStack createRuleBook(Player owner) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setTitle("Правила Death Note");
        meta.setAuthor("Death God");

        String faceRule = switch (faceMemory.getMode()) {
            case NONE -> "Знать жертву в лицо не обязательно.";
            case RADIUS -> faceMemory.getRadius() > 0
                ? "Жертва должна находиться не дальше " + (long) faceMemory.getRadius() + " блоков от вас."
                : "Жертва должна быть в сети.";
            case SEEN -> "Вы должны были видеть жертву своими глазами.";
            case FACE -> "Вы должны были видеть лицо жертвы. Маска или невидимость скрывают лицо.";
        };

        meta.addPages(
            rulesPage("HOW TO USE IT",
                rule("I", "Человек, чьё имя записано в этой тетради, умрёт."),
                rule("II", "Если указать причину смерти, всё так и случится. Если нет или она невыполнима — сердечный приступ.")),
            rulesPage(null,
                rule("III", "Можно указать, через сколько наступит смерть: от " + TimeFormat.formatLong(getMinDelay())
                    + " до " + TimeFormat.formatLong(getMaxDelay()) + ". Иначе — через " + TimeFormat.formatLong(getDefaultDelay()) + "."),
                rule("IV", faceRule)),
            rulesPage(null,
                rule("V", "Записи в тетради невозможно стереть или исправить."),
                rule("VI", "Если тетрадь уничтожена, записанное всё равно исполнится."),
                rule("VII", "Писать в тетрадь может только её владелец. Передать её нельзя.")),
            rulesPage(null,
                rule("VIII", "Вписав имя своего бога смерти, владелец умрёт, а тетрадь канет в небытие."),
                rule("IX", "Со смертью владельца тетрадь исчезает."),
                rule("X", "Глаза бога смерти стоят половины жизни. Отказавшись от тетради, вы вернёте её."))
        );

        meta.getPersistentDataContainer().set(ruleBookKey, PersistentDataType.STRING, "true");
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private Component rulesPage(String title, Component... rules) {
        var page = Component.text();
        if (title != null) {
            page.append(Component.text(title, NamedTextColor.DARK_RED, TextDecoration.BOLD)).appendNewline().appendNewline();
        }
        for (Component rule : rules) {
            page.append(rule).appendNewline().appendNewline();
        }
        return page.build();
    }

    private Component rule(String number, String text) {
        return Component.text()
            .append(Component.text(number + ". ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text(text, NamedTextColor.BLACK))
            .build();
    }

    private ItemStack createNoteItem(UUID bookId, UUID owner) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setTitle("Книга");
        meta.setAuthor("?");
        // Настоящее содержимое книга не хранит: его рисует сервер при открытии
        meta.addPages(Component.text("..."));
        meta.setDisplayName("§7Книга");
        meta.setEnchantmentGlintOverride(false);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(deathNoteKey, PersistentDataType.STRING, bookId.toString());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDeathNote(Player owner) {
        UUID bookId = UUID.randomUUID();
        storage.addNote(new NoteRecord(bookId, owner.getUniqueId(), true, 0));
        storage.save();

        // Выдаем книгу правил вместе с тетрадью
        owner.getInventory().addItem(createRuleBook(owner));
        eyes.refreshHealth(owner);
        return createNoteItem(bookId, owner.getUniqueId());
    }

    public boolean isDeathNote(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(deathNoteKey, PersistentDataType.STRING);
    }

    public boolean isRuleBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ruleBookKey, PersistentDataType.STRING);
    }

    private UUID getUuid(ItemMeta meta, NamespacedKey key) {
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The server-side record of a note item. Notes from 1.x get registered on first sight. */
    public NoteRecord getNote(ItemStack item) {
        if (!isDeathNote(item)) return null;
        ItemMeta meta = item.getItemMeta();
        UUID bookId = getUuid(meta, deathNoteKey);
        if (bookId == null) return null;

        NoteRecord note = storage.getNote(bookId);
        if (note == null) {
            UUID owner = getUuid(meta, ownerKey);
            if (owner == null) return null;
            boolean ownerHasAnother = storage.getActiveNoteOf(owner) != null;
            note = new NoteRecord(bookId, owner, !ownerHasAnother, 0);
            storage.addNote(note);
        }
        return note;
    }

    /** Replaces 1.x notes (book and quill) with the new read-only note. */
    public void convertLegacyNotes(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() != Material.WRITABLE_BOOK || !isDeathNote(item)) continue;
            NoteRecord note = getNote(item);
            inventory.setItem(i, note == null ? null : createNoteItem(note.getBookId(), note.getOwner()));
        }
    }

    public void updateBookAppearance(Player viewer, ItemStack item) {
        if (!isDeathNote(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (viewer.hasPermission("breadland.deathnote.identify")) {
            meta.setDisplayName("§4Тетрадь смерти");
        } else {
            meta.setDisplayName("§7Книга");
        }
        item.setItemMeta(meta);
    }

    // ---------------------------------------------------------------- Reading and writing

    public void openNote(Player viewer, ItemStack item) {
        NoteRecord note = getNote(item);
        if (note == null) return;

        if (!note.isActive()) {
            // Тетрадь утратила владельца — она рассыпается
            item.setAmount(0);
            sendMessage(viewer, "note-crumbled");
            return;
        }

        viewer.openBook(book.render(viewer, note, () -> runSync(() -> openWriteDialog(viewer, note.getBookId()))));
    }

    private void openWriteDialog(Player writer, UUID bookId) {
        NoteRecord note = storage.getNote(bookId);
        if (note == null || !note.isActive() || !note.getOwner().equals(writer.getUniqueId())) {
            sendMessage(writer, "not-your-book");
            return;
        }
        if (!hasNoteWithYou(writer, bookId)) {
            sendMessage(writer, "note-not-in-hands");
            return;
        }

        var info = Component.text()
            .append(Component.text("Время: от " + TimeFormat.formatLong(getMinDelay()) + " до " + TimeFormat.formatLong(getMaxDelay())
                + ", без указания — " + TimeFormat.formatLong(getDefaultDelay()) + ".", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("Причины: ", NamedTextColor.GRAY))
            .append(Component.text(String.join(", ", causes.getAll().stream().map(DeathCauses.Cause::name).toList()), NamedTextColor.WHITE));

        if (faceMemory.usesMemory()) {
            List<String> known = faceMemory.getKnownNames(writer, 12);
            info.appendNewline().appendNewline()
                .append(Component.text("Вы знаете в лицо: ", NamedTextColor.GRAY))
                .append(Component.text(known.isEmpty() ? "никого" : String.join(", ", known), NamedTextColor.WHITE));
        }

        writer.showDialog(book.writeDialog(info.build(), (player, name, cause, time) -> submitEntry(player, bookId, name, cause, time)));
    }

    private boolean hasNoteWithYou(Player player, UUID bookId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDeathNote(item) && bookId.equals(getUuid(item.getItemMeta(), deathNoteKey))) return true;
        }
        return false;
    }

    private void submitEntry(Player writer, UUID bookId, String name, String causeText, String timeText) {
        if (!config.getBoolean("death-note.enabled", true)) return;

        NoteRecord note = storage.getNote(bookId);
        if (note == null || !note.isActive() || !note.getOwner().equals(writer.getUniqueId())) {
            sendMessage(writer, "not-your-book");
            return;
        }
        if (!hasNoteWithYou(writer, bookId)) {
            sendMessage(writer, "note-not-in-hands");
            return;
        }

        long cooldownLeft = note.getCooldownSecondsLeft();
        if (cooldownLeft > 0) {
            sendMessage(writer, "cooldown",
                "%minutes%", String.valueOf(cooldownLeft / 60),
                "%seconds%", String.valueOf(cooldownLeft % 60));
            return;
        }

        if (name.isEmpty()) {
            sendMessage(writer, "player-not-found");
            return;
        }

        // Только точный ник: частичное совпадение убило бы не того
        OfflinePlayer target = plugin.getServer().getPlayerExact(name);
        if (target == null) {
            target = plugin.getServer().getOfflinePlayerIfCached(name);
        }
        if (target == null || target.getName() == null || !target.getName().equalsIgnoreCase(name)) {
            sendMessage(writer, "player-not-found");
            return;
        }

        long delay = getDefaultDelay();
        if (!timeText.isEmpty()) {
            delay = TimeFormat.parseSeconds(timeText);
            if (delay < 0) {
                sendMessage(writer, "bad-time");
                return;
            }
            if (delay < getMinDelay() || delay > getMaxDelay()) {
                sendMessage(writer, "time-out-of-range",
                    "%min%", TimeFormat.formatLong(getMinDelay()),
                    "%max%", TimeFormat.formatLong(getMaxDelay()));
                return;
            }
        }

        Player onlineTarget = target.getPlayer();

        // Игрок попытался убить своего бога смерти
        GodVanishManager godVanishManager = plugin.getGodVanishManager();
        if (onlineTarget != null && godVanishManager.isDeathGod(onlineTarget)) {
            Player boundPlayer = godVanishManager.getBoundPlayer(onlineTarget);
            if (boundPlayer != null && boundPlayer.getUniqueId().equals(writer.getUniqueId())) {
                sendMessage(writer, "death-god");
                logAction(writer.getName() + " wrote the name of their death god " + onlineTarget.getName() + " and was killed");
                loseOwnership(writer.getUniqueId());
                writer.setHealth(0);
                return;
            }
        }

        if (!faceMemory.knows(writer, target)) {
            sendMessage(writer, "too-far");
            logAction(writer.getName() + " attempted to kill " + target.getName() + " without knowing their face");
            return;
        }

        if (storage.getPendingEntryFor(target.getUniqueId()) != null) {
            sendMessage(writer, "already-written", "%player%", target.getName());
            return;
        }

        DeathCauses.Cause cause = causes.getDefault();
        if (!causeText.isEmpty()) {
            DeathCauses.Cause matched = causes.match(causeText);
            if (matched != null) {
                cause = matched;
            } else {
                sendMessage(writer, "cause-unknown");
            }
        }

        long now = System.currentTimeMillis();
        DeathEntry entry = new DeathEntry(UUID.randomUUID(), bookId, target.getUniqueId(), target.getName(),
            writer.getUniqueId(), writer.getName(), cause.id(), causeText, now, now + delay * 1000L, DeathEntry.Status.PENDING);
        note.getEntries().add(entry);
        note.setCooldownUntil(now + getCooldownMillis());
        storage.save();

        writer.playSound(writer.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
        sendMessage(writer, "countdown-started",
            "%player%", target.getName(),
            "%time%", TimeFormat.formatLong(delay),
            "%cause%", cause.name());
        logAction(writer.getName() + " wrote " + target.getName() + " (" + cause.id() + ", dies in " + delay + "s)");
    }

    // ---------------------------------------------------------------- Countdown

    private void tick() {
        long now = System.currentTimeMillis();
        for (DeathEntry entry : storage.getPendingEntries()) {
            Player victim = plugin.getServer().getPlayer(entry.getTarget());
            long left = entry.getSecondsLeft();

            // Отсчёт видит только тот, кто писал
            Player writer = plugin.getServer().getPlayer(entry.getWriter());
            if (writer != null && left > 0) {
                writer.sendActionBar(Component.text(entry.getTargetName() + " — " + TimeFormat.formatClock(left), NamedTextColor.DARK_RED));
            }

            if (victim == null || victim.isDead()) continue;

            if (entry.getDeathAt() <= now) {
                killByNote(victim, entry);
            } else if (left <= 5) {
                victim.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
            }
        }
    }

    private void killByNote(Player victim, DeathEntry entry) {
        DeathCauses.Cause cause = causes.get(entry.getCauseId());
        logAction(entry.getWriterName() + " killed " + victim.getName() + " using Death Note (" + cause.id() + ")");

        entry.setStatus(DeathEntry.Status.DEAD);
        storage.save();

        UUID victimUUID = victim.getUniqueId();
        dyingFromNote.put(victimUUID, cause.id());
        cause.playEffect(victim);
        victim.setHealth(0);
        // Если смерть отменил другой плагин, не оставляем метку на следующую смерть
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> dyingFromNote.remove(victimUUID), 1L);
    }

    // ---------------------------------------------------------------- Ownership

    /**
     * The owner loses their note: it disappears from the world, the shinigami eyes go away
     * and the health they cost is returned. Written entries still happen (rule VI).
     */
    public void loseOwnership(UUID owner) {
        NoteRecord note = storage.getActiveNoteOf(owner);
        while (note != null) {
            note.setActive(false);
            note = storage.getActiveNoteOf(owner);
        }
        storage.markDirty();

        forceRemoveDeathNoteFromWorld(owner);
        faceMemory.forget(owner);
        eyes.revoke(owner);
        storage.save();
    }

    private boolean isOwnedNoteOrRules(ItemStack item, UUID ownerUUID) {
        if (!isDeathNote(item) && !isRuleBook(item)) return false;
        return ownerUUID.equals(getUuid(item.getItemMeta(), ownerKey));
    }

    private void removeNotesFromInventory(Inventory inventory, UUID ownerUUID) {
        // Для игрока сюда входят и броня, и вторая рука
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isOwnedNoteOrRules(inventory.getItem(i), ownerUUID)) {
                inventory.setItem(i, null);
            }
        }
    }

    private void forceRemoveDeathNoteFromWorld(UUID ownerUUID) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeNotesFromInventory(player.getInventory(), ownerUUID);
            removeNotesFromInventory(player.getEnderChest(), ownerUUID);
        }

        plugin.getServer().getWorlds().forEach(world -> {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isOwnedNoteOrRules(item.getItemStack(), ownerUUID)) {
                    item.remove();
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof Container container) {
                        removeNotesFromInventory(container.getInventory(), ownerUUID);
                    }
                }
            }
        });
        // Тетради, оставшиеся в незагруженных чанках, рассыпятся при первом открытии
    }

    // ---------------------------------------------------------------- Admin actions

    public boolean giveNote(CommandSender sender, Player target) {
        if (storage.getActiveNoteOf(target.getUniqueId()) != null) {
            sendMessage(sender, "already-owner", "%player%", target.getName());
            return false;
        }
        target.getInventory().addItem(createDeathNote(target));
        sendMessage(target, "received");
        logAction(sender.getName() + " gave a Death Note to " + target.getName());
        return true;
    }

    public void offerEyes(CommandSender admin, Player target) {
        if (storage.getActiveNoteOf(target.getUniqueId()) == null) {
            sendMessage(admin, "eyes-not-owner", "%player%", target.getName());
            return;
        }
        if (eyes.hasEyes(target.getUniqueId())) {
            sendMessage(admin, "eyes-already", "%player%", target.getName());
            return;
        }

        Component description = Component.text()
            .append(Component.text("Бог смерти предлагает тебе свои глаза.", NamedTextColor.WHITE))
            .appendNewline().appendNewline()
            .append(Component.text("Ты будешь видеть настоящее имя каждого, на кого посмотришь, и сколько осталось жить тем, кто уже вписан в тетрадь.", NamedTextColor.GRAY))
            .appendNewline().appendNewline()
            .append(Component.text("Цена — половина твоей жизни. Пока тетрадь твоя, у тебя будет вдвое меньше сердец. Потеряешь тетрадь — вернёшь их, но лишишься глаз.", NamedTextColor.RED))
            .build();

        target.showDialog(book.dealDialog(description,
            player -> {
                if (storage.getActiveNoteOf(player.getUniqueId()) == null || eyes.hasEyes(player.getUniqueId())) return;
                eyes.grant(player);
                storage.save();
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
                sendMessage(player, "eyes-accepted");
                sendMessage(admin, "eyes-admin-accepted", "%player%", player.getName());
                logAction(player.getName() + " accepted the shinigami eyes deal");
            },
            player -> {
                sendMessage(player, "eyes-declined");
                sendMessage(admin, "eyes-admin-declined", "%player%", player.getName());
            }));
        sendMessage(admin, "eyes-offered", "%player%", target.getName());
    }

    // ---------------------------------------------------------------- Events

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        event.getDrops().removeIf(item -> isDeathNote(item) || isRuleBook(item));

        // Со смертью владельца тетрадь исчезает
        if (storage.getActiveNoteOf(player.getUniqueId()) != null) {
            logAction("Death Note owner " + player.getName() + " died, the note is gone");
            loseOwnership(player.getUniqueId());
        }

        String causeId = dyingFromNote.remove(player.getUniqueId());
        if (causeId != null) {
            String deathMessage = causes.get(causeId).deathMessage().replace("%player%", player.getName());
            event.setDeathMessage(ChatColor.translateAlternateColorCodes('&', deathMessage));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        convertLegacyNotes(player.getInventory());
        convertLegacyNotes(player.getEnderChest());
        // Сердца могли поменяться, пока игрок был оффлайн (например, админ отобрал тетрадь)
        eyes.refreshHealth(player);
        // Просроченные записи на этого игрока исполнит tick(), как только он войдёт
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        eyes.removeLabel(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- Messages

    private void runSync(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) {
            action.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, action);
        }
    }

    public String getMessage(String path) {
        String message = config.getString("death-note.messages." + path);
        return message != null ? message : "§cСообщение не найдено: " + path;
    }

    public void sendMessage(CommandSender receiver, String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void logAction(String action) {
        plugin.getLogger().info("[Death Note] " + action + " at " + LocalDateTime.now().format(LOG_TIME));
    }

    public List<DeathEntry> getPendingEntries() {
        return new ArrayList<>(storage.getPendingEntries());
    }
}

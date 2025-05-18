package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import me.tedecti.managers.GodVanishManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeathNoteManager implements Listener {
    private final BreadlandPlugin plugin;
    private final Map<UUID, String> activeDeathNotes; // Book UUID -> Target Player Name
    private final Set<String> pendingDeaths; // Player Name
    private final Map<UUID, Long> cooldowns; // Book UUID -> Cooldown End Time
    private final NamespacedKey deathNoteKey;
    private final NamespacedKey ownerKey;
    private FileConfiguration config;
    private static final long COOLDOWN_DURATION = 30 * 60 * 1000; // 30 minutes in milliseconds

    public DeathNoteManager(BreadlandPlugin plugin) {
        this.plugin = plugin;
        this.activeDeathNotes = new HashMap<>();
        this.pendingDeaths = new HashSet<>();
        this.cooldowns = new HashMap<>();
        this.deathNoteKey = new NamespacedKey(plugin, "death_note");
        this.ownerKey = new NamespacedKey(plugin, "death_note_owner");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reloadConfig();
    }

    public void reloadConfig() {
        this.config = plugin.getConfigManager().getConfig("events.yml");
        if (this.config == null) {
            plugin.getLogger().warning("Failed to load events.yml configuration!");
        }
    }

    private ItemStack createRuleBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("Правила Death Note");
            meta.setAuthor("Death God");
            
            List<String> pages = new ArrayList<>();
            
            pages.add("§4Правила использования\n§0Death Note\n\n" +
                "§01. Игрок, чьё имя будет записано в тетради смерти, умрёт.\n\n" +
                "§02. Если причина смерти написана в течение 40 секунд после записи имени, то так оно и ");
  

            // Страница 2
            pages.add("случится. §03. Если причина смерти не указана, через 40 секунд указанный игрок умрёт от сердечного приступа.\n\n" +
                "§04. Если вписать ник бога смерти, то бог смерти умрёт вместе с носителем тетради и та канет в небытие.\n\n");
  

            // Страница 3
            pages.add("§05. Что бы всё сработало, нужно точно вписать ник игрока в тетрадь и нажать -> готово.\n\n" +
                "§06. Если тетрадь будет уничтожена, все её эффекты останутся в силе.\n\n");
            pages.add(
                "§07. Владелец тетради не может передать её другому игроку.\n\n" +
                "§08. Нарушение правил карается смертью.");


            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        
        return book;
    }

    public ItemStack createDeathNote(Player owner) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§7Книга");
            meta.getPersistentDataContainer().set(deathNoteKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            book.setItemMeta(meta);
            
            // Выдаем книгу правил вместе с тетрадью
            owner.getInventory().addItem(createRuleBook());
        }
        
        return book;
    }

    public boolean isDeathNote(ItemStack item) {
        if (item == null || item.getType() != Material.WRITABLE_BOOK) return false;
        
        BookMeta meta = (BookMeta) item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(deathNoteKey, PersistentDataType.STRING);
    }

    public boolean canUseDeathNote(Player player, ItemStack book) {
        if (!isDeathNote(book)) return false;
        
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return false;

        String ownerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerUUID == null || !ownerUUID.equals(player.getUniqueId().toString())) {
            return false;
        }

        // Check cooldown
        String bookId = meta.getPersistentDataContainer().get(deathNoteKey, PersistentDataType.STRING);
        if (bookId == null) return false;

        UUID bookUUID = UUID.fromString(bookId);
        Long cooldownEnd = cooldowns.get(bookUUID);
        if (cooldownEnd != null) {
            long timeLeft = (cooldownEnd - System.currentTimeMillis()) / 1000; // Convert to seconds
            if (timeLeft > 0) {
                long minutes = timeLeft / 60;
                long seconds = timeLeft % 60;
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    getMessage("cooldown")
                        .replace("%minutes%", String.valueOf(minutes))
                        .replace("%seconds%", String.valueOf(seconds))));
                return false;
            } else {
                cooldowns.remove(bookUUID);
            }
        }

        return true;
    }

    public void updateBookAppearance(Player viewer, ItemStack book) {
        if (!isDeathNote(book)) return;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        if (viewer.hasPermission("breadland.deathnote.identify")) {
            meta.setDisplayName("§4Тетрадь смерти");
        } else {
            meta.setDisplayName("§7Книга");
        }
        book.setItemMeta(meta);
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        ItemStack book = event.getPlayer().getInventory().getItemInMainHand();
        if (!isDeathNote(book)) return;

        if (event.isSigning()) {
            BookMeta meta = event.getNewBookMeta();
            String ownerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (ownerUUID != null) {
                Player owner = plugin.getServer().getPlayer(UUID.fromString(ownerUUID));
                if (owner != null && owner.isOnline()) {
                    owner.setHealth(0);
                    owner.sendMessage("§4Ваша тетрадь была подписана. Вы умерли!");
                }
            }
            // Сохраняем подпись, но не удаляем тетрадь
            event.setNewBookMeta(meta);
        }
    }

    public void activateDeathNote(ItemStack book, String targetPlayer, Player user) {
        if (config == null || !config.getBoolean("death-note.enabled", true)) return;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        String bookId = meta.getPersistentDataContainer().get(deathNoteKey, PersistentDataType.STRING);
        if (bookId == null) return;

        String ownerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerUUID == null) return;

        UUID bookUUID = UUID.fromString(bookId);
        Player owner = plugin.getServer().getPlayer(UUID.fromString(ownerUUID));
        
        // Проверяем, не является ли цель богом смерти владельца тетради
        if (owner != null) {
            GodVanishManager godVanishManager = plugin.getGodVanishManager();
            Player targetPlayerObj = plugin.getServer().getPlayer(targetPlayer);
            
            if (targetPlayerObj != null && godVanishManager.isDeathGod(targetPlayerObj)) {
                Player boundPlayer = godVanishManager.getBoundPlayer(targetPlayerObj);
                if (boundPlayer != null && boundPlayer.getUniqueId().toString().equals(ownerUUID)) {
                    // Игрок попытался убить своего бога смерти
                    owner.setHealth(0);
                    owner.sendMessage("§4Вы посмели написать имя своего бога смерти. Ваша тетрадь уничтожена.");
                    
                    // Принудительно удаляем все тетради этого владельца из мира
                    forceRemoveDeathNoteFromWorld(UUID.fromString(ownerUUID));
                    
                    // Очищаем все связанные данные
                    activeDeathNotes.remove(bookUUID);
                    cooldowns.remove(bookUUID);
                    return;
                }
            }
        }

        // Проверяем кулдаун только если это не попытка убить бога смерти
        Long cooldownEnd = cooldowns.get(bookUUID);
        if (cooldownEnd != null) {
            long timeLeft = (cooldownEnd - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                long minutes = timeLeft / 60;
                long seconds = timeLeft % 60;
                user.sendMessage(getMessage("cooldown")
                    .replace("%minutes%", String.valueOf(minutes))
                    .replace("%seconds%", String.valueOf(seconds)));
                return;
            }
        }

        activeDeathNotes.put(bookUUID, targetPlayer);
        pendingDeaths.add(targetPlayer);
        cooldowns.put(bookUUID, System.currentTimeMillis() + COOLDOWN_DURATION);

        // Schedule death after configured delay
        int delay = config.getInt("death-note.death-delay", 60);
        new BukkitRunnable() {
            @Override
            public void run() {
                String target = activeDeathNotes.remove(bookUUID);
                if (target != null) {
                    Player targetPlayer = plugin.getServer().getPlayer(target);
                    if (targetPlayer != null) {
                        targetPlayer.setHealth(0);
                    }
                }
            }
        }.runTaskLater(plugin, 20 * delay);
    }

    private void removeDeathNoteFromInventory(PlayerInventory inventory, UUID ownerUUID) {
        // Проверяем основной инвентарь
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && isDeathNote(item)) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null) {
                    String itemOwnerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (itemOwnerUUID != null && (itemOwnerUUID.equals(ownerUUID.toString()) || ownerUUID == null)) {
                        inventory.setItem(i, null);
                        String bookId = meta.getPersistentDataContainer().get(deathNoteKey, PersistentDataType.STRING);
                        if (bookId != null) {
                            activeDeathNotes.remove(UUID.fromString(bookId));
                            cooldowns.remove(UUID.fromString(bookId));
                        }
                    }
                }
            }
        }

        // Проверяем слот брони (если вдруг тетрадь там)
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && isDeathNote(item)) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null) {
                    String itemOwnerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (itemOwnerUUID != null && (itemOwnerUUID.equals(ownerUUID.toString()) || ownerUUID == null)) {
                        inventory.remove(item);
                    }
                }
            }
        }

        // Проверяем оффхенд
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && isDeathNote(offhand)) {
            BookMeta meta = (BookMeta) offhand.getItemMeta();
            if (meta != null) {
                String itemOwnerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (itemOwnerUUID != null && (itemOwnerUUID.equals(ownerUUID.toString()) || ownerUUID == null)) {
                    inventory.setItemInOffHand(null);
                }
            }
        }
    }

    private void forceRemoveDeathNoteFromWorld(UUID ownerUUID) {
        // Удаляем из инвентарей всех онлайн игроков
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeDeathNoteFromInventory(player.getInventory(), ownerUUID);
        }

        // Удаляем все тетради смерти с земли и из контейнеров
        plugin.getServer().getWorlds().forEach(world -> {
            // Проверяем предметы на земле
            world.getEntities().forEach(entity -> {
                if (entity instanceof org.bukkit.entity.Item) {
                    org.bukkit.entity.Item item = (org.bukkit.entity.Item) entity;
                    ItemStack itemStack = item.getItemStack();
                    
                    if (isDeathNote(itemStack)) {
                        BookMeta meta = (BookMeta) itemStack.getItemMeta();
                        if (meta != null) {
                            String itemOwnerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                            if (itemOwnerUUID != null && (itemOwnerUUID.equals(ownerUUID.toString()) || ownerUUID == null)) {
                                item.remove(); // Полностью удаляем предмет из мира
                            }
                        }
                    }
                }
            });

            // Проверяем все загруженные чанки на наличие контейнеров
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof org.bukkit.block.Container) {
                        org.bukkit.block.Container container = (org.bukkit.block.Container) blockState;
                        org.bukkit.inventory.Inventory inventory = container.getInventory();
                        
                        for (int i = 0; i < inventory.getSize(); i++) {
                            ItemStack item = inventory.getItem(i);
                            if (item != null && isDeathNote(item)) {
                                BookMeta meta = (BookMeta) item.getItemMeta();
                                if (meta != null) {
                                    String itemOwnerUUID = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                                    if (itemOwnerUUID != null && (itemOwnerUUID.equals(ownerUUID.toString()) || ownerUUID == null)) {
                                        inventory.setItem(i, null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();

        // Жесткое удаление всех тетрадей смерти из мира
        forceRemoveDeathNoteFromWorld(playerUUID);

        // Удаляем тетради из дропа при смерти
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isDeathNote(item)) {
                iterator.remove();
            }
        }

        // Проверяем, была ли это смерть от тетради
        if (isDeathNoteDeath(player.getName())) {
            String deathMessage = getMessage("death")
                .replace("%player%", player.getName());
            event.setDeathMessage(ChatColor.translateAlternateColorCodes('&', deathMessage));
        }
    }

    public String getTargetPlayer(ItemStack book) {
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return null;

        String bookId = meta.getPersistentDataContainer().get(deathNoteKey, PersistentDataType.STRING);
        if (bookId == null) return null;

        return activeDeathNotes.get(UUID.fromString(bookId));
    }

    public String getMessage(String path) {
        if (config == null) {
            return "§cОшибка конфигурации!";
        }
        return config.getString("death-note.messages." + path, "§cСообщение не найдено!");
    }

    public boolean isDeathNoteDeath(String playerName) {
        return pendingDeaths.remove(playerName);
    }
} 
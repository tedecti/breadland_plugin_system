package me.tedecti.events.deathnote;

import me.tedecti.BreadlandPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class DeathNoteHandler implements Listener {
    private final BreadlandPlugin plugin;
    private final DeathNoteManager deathNoteManager;

    public DeathNoteHandler(BreadlandPlugin plugin, DeathNoteManager deathNoteManager) {
        this.plugin = plugin;
        this.deathNoteManager = deathNoteManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (!deathNoteManager.isDeathNote(item)) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        // Сундуки, двери и т.п. открываются как обычно; кафедра — нет, иначе книгу прочитают все
        if (block != null && block.getType() != Material.LECTERN && block.getType().isInteractable() && !player.isSneaking()) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        if (item.getType() == Material.WRITABLE_BOOK) {
            // Тетрадь из версии 1.x: меняем на новую
            deathNoteManager.convertLegacyNotes(player.getInventory());
            item = player.getInventory().getItem(hand);
            if (!deathNoteManager.isDeathNote(item)) return;
        }

        deathNoteManager.updateBookAppearance(player, item);
        deathNoteManager.openNote(player, item);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // Копирование подписанной книги размножило бы тетрадь
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (deathNoteManager.isDeathNote(ingredient) || deathNoteManager.isRuleBook(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!(event.getBlock().getState() instanceof Crafter crafter)) return;
        for (ItemStack ingredient : crafter.getInventory().getContents()) {
            if (deathNoteManager.isDeathNote(ingredient) || deathNoteManager.isRuleBook(ingredient)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!deathNoteManager.isDeathNote(item.getItemStack())) return;

        // Выброшенная тетрадь не горит в лаве и не исчезает со временем
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (deathNoteManager.isDeathNote(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemFallOutOfWorld(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.OUT_OF_WORLD || !(event.getEntity() instanceof Item item)) return;

        ItemStack stack = item.getItemStack().clone();
        NoteRecord note = deathNoteManager.getNote(stack);
        if (note == null || !note.isActive()) return;

        // Тетрадь, упавшая в пустоту, возвращается к владельцу
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player owner = plugin.getServer().getPlayer(note.getOwner());
            if (owner == null) return;
            owner.getInventory().addItem(stack).values()
                .forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
            deathNoteManager.sendMessage(owner, "note-returned");
        });
    }
}

package me.tedecti.events.deathnote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.tedecti.BreadlandPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds what players see: the formatted note (rendered fresh from storage on every open,
 * so nothing in it can be edited or erased) and the native input dialogs.
 */
public class DeathNoteBook {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int ENTRIES_PER_PAGE = 4;
    private static final int INPUT_WIDTH = 250;
    private static final ClickCallback.Options ONE_USE = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(10))
        .build();

    private final BreadlandPlugin plugin;
    public DeathNoteBook(BreadlandPlugin plugin) {
        this.plugin = plugin;
    }

    public Book render(Player viewer, NoteRecord note, Runnable onWrite) {
        List<Component> pages = new ArrayList<>();
        pages.add(renderCover(viewer, note, onWrite));

        List<DeathEntry> entries = note.getEntries();
        for (int start = 0; start < entries.size(); start += ENTRIES_PER_PAGE) {
            TextComponent.Builder page = Component.text();
            for (DeathEntry entry : entries.subList(start, Math.min(start + ENTRIES_PER_PAGE, entries.size()))) {
                page.append(renderEntry(entry)).appendNewline().appendNewline();
            }
            pages.add(page.build());
        }

        return Book.book(Component.text("Death Note"), Component.text("?"), pages);
    }

    private Component renderCover(Player viewer, NoteRecord note, Runnable onWrite) {
        String ownerName = plugin.getServer().getOfflinePlayer(note.getOwner()).getName();
        TextComponent.Builder cover = Component.text()
            .appendNewline()
            .append(Component.text("    DEATH NOTE", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .appendNewline()
            .append(Component.text("  ━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            .appendNewline().appendNewline()
            .append(Component.text("Владелец: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(ownerName == null ? "?" : ownerName, NamedTextColor.BLACK))
            .appendNewline().appendNewline();

        boolean isOwner = viewer.getUniqueId().equals(note.getOwner());
        if (!note.isActive()) {
            cover.append(Component.text("Тетрадь мертва.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        } else if (isOwner && note.getCooldownSecondsLeft() > 0) {
            cover.append(Component.text("Тетрадь отдыхает", NamedTextColor.GRAY, TextDecoration.ITALIC))
                .appendNewline()
                .append(Component.text("ещё " + TimeFormat.formatLong(note.getCooldownSecondsLeft()), NamedTextColor.GRAY));
        } else if (isOwner) {
            cover.append(Component.text("  [✎ Вписать имя]", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы вписать имя", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.callback(audience -> onWrite.run(), ONE_USE)));
        }

        long dead = note.getEntries().stream().filter(entry -> entry.getStatus() == DeathEntry.Status.DEAD).count();
        return cover.appendNewline().appendNewline().appendNewline()
            .append(Component.text("Записей: " + note.getEntries().size(), NamedTextColor.DARK_GRAY))
            .appendNewline()
            .append(Component.text("Исполнено: " + dead, NamedTextColor.DARK_GRAY))
            .build();
    }

    private Component renderEntry(DeathEntry entry) {
        Component hover = Component.text()
            .append(Component.text(entry.getTargetName(), NamedTextColor.RED, TextDecoration.BOLD)).appendNewline()
            .append(Component.text("Вписано: " + DATE.format(Instant.ofEpochMilli(entry.getWrittenAt())), NamedTextColor.GRAY)).appendNewline()
            .append(Component.text("Смерть: " + DATE.format(Instant.ofEpochMilli(entry.getDeathAt())), NamedTextColor.GRAY)).appendNewline()
            .append(Component.text("Вписал: " + entry.getWriterName(), NamedTextColor.DARK_GRAY))
            .build();

        Component name;
        Component status;
        switch (entry.getStatus()) {
            case DEAD -> {
                name = Component.text(entry.getTargetName(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD, TextDecoration.STRIKETHROUGH);
                status = Component.text("✝ " + DATE.format(Instant.ofEpochMilli(entry.getDeathAt())), NamedTextColor.DARK_GRAY);
            }
            case CANCELLED -> {
                name = Component.text(entry.getTargetName(), NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH);
                status = Component.text("утратило силу", NamedTextColor.GRAY, TextDecoration.ITALIC);
            }
            default -> {
                name = Component.text(entry.getTargetName(), NamedTextColor.BLACK, TextDecoration.BOLD);
                status = Component.text("через " + TimeFormat.formatClock(entry.getSecondsLeft()), NamedTextColor.DARK_RED);
            }
        }

        return Component.text()
            .append(name.hoverEvent(HoverEvent.showText(hover)))
            .appendNewline()
            .append(status)
            .build();
    }

    /** The native form for writing a name. Calls onSubmit with (name, time). */
    public Dialog writeDialog(Component info, WriteHandler onSubmit) {
        List<DialogInput> inputs = List.of(
            DialogInput.text("name", Component.text("Имя жертвы")).width(INPUT_WIDTH).maxLength(16).build(),
            DialogInput.text("time", Component.text("Время смерти")).width(INPUT_WIDTH).maxLength(16).build()
        );

        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Death Note", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(info, 300)))
                .inputs(inputs)
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Вписать", NamedTextColor.DARK_RED))
                    .action(callback((view, audience) -> {
                        if (audience instanceof Player player) {
                            onSubmit.submit(player, text(view, "name"), text(view, "time"));
                        }
                    }))
                    .build(),
                ActionButton.builder(Component.text("Отмена")).build())));
    }

    /** Yes/no dialog for the shinigami eyes deal. */
    public Dialog dealDialog(Component description, Consumer<Player> onAccept, Consumer<Player> onDecline) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Сделка с богом смерти", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .canCloseWithEscape(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(description, 300)))
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Заключить сделку", NamedTextColor.DARK_RED))
                    .action(callback((view, audience) -> {
                        if (audience instanceof Player player) onAccept.accept(player);
                    }))
                    .build(),
                ActionButton.builder(Component.text("Отказаться"))
                    .action(callback((view, audience) -> {
                        if (audience instanceof Player player) onDecline.accept(player);
                    }))
                    .build())));
    }

    private DialogAction callback(BiConsumer<DialogResponseView, Audience> action) {
        return DialogAction.customClick((view, audience) -> {
            // Колбэк может прийти не из основного потока — вся логика тетради работает в нём
            if (plugin.getServer().isPrimaryThread()) {
                action.accept(view, audience);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> action.accept(view, audience));
            }
        }, ONE_USE);
    }

    private static String text(DialogResponseView view, String key) {
        String value = view.getText(key);
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    public interface WriteHandler {
        void submit(Player writer, String name, String time);
    }
}

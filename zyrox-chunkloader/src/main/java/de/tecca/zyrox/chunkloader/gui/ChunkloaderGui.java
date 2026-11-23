package de.tecca.zyrox.chunkloader.gui;

import de.tecca.zyrox.chunkloader.service.ChunkloaderService;
import de.tecca.zyrox.chunkloader.service.ChunkloaderStatus;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChunkloaderGui {

    public static final int SLOT_INFO = 4;
    public static final int SLOT_TOGGLE = 10;
    public static final int SLOT_DEPOSIT_1 = 11;
    public static final int SLOT_DEPOSIT_8 = 12;
    public static final int SLOT_DEPOSIT_32 = 13;
    public static final int SLOT_DEPOSIT_ALL = 14;
    public static final int SLOT_PRIORITY = 16;
    public static final int SLOT_CLOSE = 22;
    public static final int[] PRIORITY_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private ChunkloaderGui() {
    }

    public static Inventory main(ChunkloaderRecord loader, ChunkloaderService service) {
        ChunkloaderHolder holder = new ChunkloaderHolder(loader);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Chunkloader #" + loader.id());
        renderMain(inventory, loader, service);
        return inventory;
    }

    public static void renderMain(Inventory inventory, ChunkloaderRecord loader, ChunkloaderService service) {
        inventory.clear();
        Map<String, String> placeholders = service.placeholders(loader);
        inventory.setItem(SLOT_INFO, button(service.blockMaterial(), "Chunkloader #" + loader.id(), List.of(
                "Besitzer: " + placeholders.get("owner"),
                "Ort: " + placeholders.get("world") + " " + placeholders.get("x") + "/" + placeholders.get("y") + "/" + placeholders.get("z"),
                "Chunk: " + placeholders.get("chunk_x") + "/" + placeholders.get("chunk_z"),
                "Status: " + placeholders.get("status"),
                "Rang: " + placeholders.get("rank"),
                "Reserve: " + placeholders.get("reserve") + " DiaBlock(s)",
                "Naechste Kosten: " + placeholders.get("next_cost") + " DiaBlock(s)",
                "Bezahlt: " + placeholders.get("paid_time_left")
        )));
        inventory.setItem(SLOT_TOGGLE, button(loader.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                loader.enabled() ? "Aktiviert" : "Deaktiviert",
                List.of("Klicke zum Umschalten.")));
        inventory.setItem(SLOT_DEPOSIT_1, button(Material.DIAMOND_BLOCK, "+1 DiaBlock", List.of("Lagert 1 Diamantblock ein.")));
        inventory.setItem(SLOT_DEPOSIT_8, button(Material.DIAMOND_BLOCK, "+8 DiaBlocks", List.of("Lagert 8 Diamantbloecke ein.")));
        inventory.setItem(SLOT_DEPOSIT_32, button(Material.DIAMOND_BLOCK, "+32 DiaBlocks", List.of("Lagert 32 Diamantbloecke ein.")));
        inventory.setItem(SLOT_DEPOSIT_ALL, button(Material.HOPPER, "Alle DiaBlocks", List.of("Lagert alle Diamantbloecke aus deinem Inventar ein.")));
        inventory.setItem(SLOT_PRIORITY, button(Material.COMPASS, "Prioritaet", List.of("Sortiert deine Chunkloader.", "Guenstigere Ranges stehen weiter oben.")));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "Schliessen", List.of()));
    }

    public static void renderPriority(Inventory inventory, ChunkloaderHolder holder, List<ChunkloaderRecord> loaders, ChunkloaderService service) {
        inventory.clear();
        holder.view(ChunkloaderHolder.View.PRIORITY);
        inventory.setItem(4, button(Material.COMPASS, "Chunkloader Prioritaet", List.of(
                "Linksklick: hoch",
                "Rechtsklick: runter"
        )));
        for (int index = 0; index < loaders.size() && index < PRIORITY_SLOTS.length; index++) {
            ChunkloaderRecord loader = loaders.get(index);
            ChunkloaderStatus status = service.status(loader);
            inventory.setItem(PRIORITY_SLOTS[index], button(service.blockMaterial(), "#" + loader.id() + " - Prioritaet " + loader.priority(), List.of(
                    loader.worldName() + " " + loader.x() + "/" + loader.y() + "/" + loader.z(),
                    "Chunk: " + loader.chunkX() + "/" + loader.chunkZ(),
                    "Status: " + (status.active() ? "aktiv" : "inaktiv"),
                    "Rang: " + (status.rank() <= 0 ? "-" : status.rank()),
                    "Reserve: " + status.reserveBlocks() + " DiaBlock(s)",
                    "Linksklick: hoch",
                    "Rechtsklick: runter"
            )));
        }
        inventory.setItem(SLOT_CLOSE, button(Material.ARROW, "Zurueck", List.of("Zur Hauptansicht.")));
    }

    public static Long priorityLoaderAt(int rawSlot, List<ChunkloaderRecord> loaders) {
        for (int index = 0; index < PRIORITY_SLOTS.length && index < loaders.size(); index++) {
            if (PRIORITY_SLOTS[index] == rawSlot) {
                return loaders.get(index).id();
            }
        }
        return null;
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}

package de.tecca.zyrox.chunkloader.gui;

import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChunkloaderHolder implements InventoryHolder {

    public enum View {
        MAIN,
        PRIORITY
    }

    private ChunkloaderRecord loader;
    private View view = View.MAIN;

    public ChunkloaderHolder(ChunkloaderRecord loader) {
        this.loader = loader;
    }

    public ChunkloaderRecord loader() {
        return loader;
    }

    public void update(ChunkloaderRecord loader) {
        this.loader = loader;
    }

    public View view() {
        return view;
    }

    public void view(View view) {
        this.view = view;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

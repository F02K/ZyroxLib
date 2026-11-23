package de.tecca.zyrox.chunkloader.listener;

import de.tecca.zyrox.chunkloader.gui.ChunkloaderGui;
import de.tecca.zyrox.chunkloader.gui.ChunkloaderHolder;
import de.tecca.zyrox.chunkloader.item.ChunkloaderItems;
import de.tecca.zyrox.chunkloader.plugin.ZyroxChunkloaderPlugin;
import de.tecca.zyrox.chunkloader.service.ChunkloaderActionResult;
import de.tecca.zyrox.chunkloader.service.ChunkloaderService;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkloaderListener implements Listener {

    private final ZyroxChunkloaderPlugin plugin;
    private final ChunkloaderService service;
    private final ChunkloaderItems items;
    private final Map<String, Long> clickCooldowns = new ConcurrentHashMap<>();

    public ChunkloaderListener(ZyroxChunkloaderPlugin plugin, ChunkloaderService service, ChunkloaderItems items) {
        this.plugin = plugin;
        this.service = service;
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!items.isChunkloaderItem(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(plugin.usePermission())) {
            event.setCancelled(true);
            player.sendMessage(plugin.text().message("no-use-permission", Map.of()));
            return;
        }
        ChunkloaderActionResult result = service.create(event.getBlockPlaced(), player);
        if (!result.success()) {
            event.setCancelled(true);
        }
        player.sendMessage(result.message());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Optional<ChunkloaderRecord> loader = service.findLoader(event.getClickedBlock().getLocation());
        if (loader.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canAccess(player, loader.get())) {
            player.sendMessage(plugin.text().message("only-owner", service.placeholders(loader.get())));
            return;
        }
        player.openInventory(ChunkloaderGui.main(loader.get(), service));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<ChunkloaderRecord> loader = service.findLoader(event.getBlock().getLocation());
        if (loader.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!canAccess(player, loader.get())) {
            event.setCancelled(true);
            player.sendMessage(plugin.text().message("break-protected", service.placeholders(loader.get())));
            return;
        }
        event.setDropItems(false);
        ChunkloaderRecord record = loader.get();
        ChunkloaderActionResult result = service.delete(record);
        if (!result.success()) {
            event.setCancelled(true);
            player.sendMessage(result.message());
            return;
        }
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), items.createItem(1));
        dropReserve(event.getBlock(), record.reserveBlocks());
        player.sendMessage(plugin.text().message("break-success", service.placeholders(record)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof ChunkloaderHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        Optional<ChunkloaderRecord> current = service.findLoader(holder.loader().id());
        if (current.isEmpty()) {
            player.closeInventory();
            return;
        }
        if (!canAccess(player, current.get())) {
            player.closeInventory();
            player.sendMessage(plugin.text().message("only-owner", service.placeholders(current.get())));
            return;
        }
        if (holder.view() == ChunkloaderHolder.View.PRIORITY) {
            handlePriorityClick(player, event.getRawSlot(), event.getClick(), event.getInventory(), holder);
            return;
        }
        handleMainClick(player, event.getRawSlot(), event.getInventory(), holder, current.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(service::isChunkloaderBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(service::isChunkloaderBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isChunkloaderBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isChunkloaderBlock)) {
            event.setCancelled(true);
        }
    }

    private void handleMainClick(Player player, int rawSlot, Inventory inventory, ChunkloaderHolder holder, ChunkloaderRecord loader) {
        if (rawSlot == ChunkloaderGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (isCoolingDown(player.getUniqueId(), loader.id(), "main")) {
            return;
        }
        ChunkloaderActionResult result = null;
        if (rawSlot == ChunkloaderGui.SLOT_TOGGLE) {
            result = service.toggle(loader);
        } else if (rawSlot == ChunkloaderGui.SLOT_DEPOSIT_1) {
            result = service.deposit(loader, player, 1);
        } else if (rawSlot == ChunkloaderGui.SLOT_DEPOSIT_8) {
            result = service.deposit(loader, player, 8);
        } else if (rawSlot == ChunkloaderGui.SLOT_DEPOSIT_32) {
            result = service.deposit(loader, player, 32);
        } else if (rawSlot == ChunkloaderGui.SLOT_DEPOSIT_ALL) {
            result = service.deposit(loader, player, -1);
        } else if (rawSlot == ChunkloaderGui.SLOT_PRIORITY) {
            List<ChunkloaderRecord> loaders = service.loadersByOwner(loader.ownerUuid());
            ChunkloaderGui.renderPriority(inventory, holder, loaders, service);
            return;
        }
        if (result != null) {
            player.sendMessage(result.message());
            ChunkloaderRecord updated = result.loader() == null ? loader : result.loader();
            holder.update(updated);
            ChunkloaderGui.renderMain(inventory, updated, service);
        }
    }

    private void handlePriorityClick(Player player, int rawSlot, ClickType click, Inventory inventory, ChunkloaderHolder holder) {
        if (rawSlot == ChunkloaderGui.SLOT_CLOSE) {
            holder.view(ChunkloaderHolder.View.MAIN);
            ChunkloaderRecord current = service.findLoader(holder.loader().id()).orElse(holder.loader());
            holder.update(current);
            ChunkloaderGui.renderMain(inventory, current, service);
            return;
        }
        List<ChunkloaderRecord> loaders = service.loadersByOwner(holder.loader().ownerUuid());
        Long loaderId = ChunkloaderGui.priorityLoaderAt(rawSlot, loaders);
        if (loaderId == null) {
            return;
        }
        ChunkloaderRecord target = service.findLoader(loaderId).orElse(null);
        if (target == null || isCoolingDown(player.getUniqueId(), target.id(), "priority")) {
            return;
        }
        int direction = click == ClickType.RIGHT ? 1 : -1;
        ChunkloaderActionResult result = service.movePriority(target, direction);
        player.sendMessage(result.message());
        ChunkloaderRecord current = service.findLoader(holder.loader().id()).orElse(holder.loader());
        holder.update(current);
        ChunkloaderGui.renderPriority(inventory, holder, service.loadersByOwner(current.ownerUuid()), service);
    }

    private boolean canAccess(Player player, ChunkloaderRecord loader) {
        return loader.isOwner(player.getUniqueId()) || player.hasPermission(plugin.adminPermission());
    }

    private boolean isCoolingDown(UUID playerId, long loaderId, String action) {
        String key = playerId + ":" + loaderId + ":" + action;
        long now = System.currentTimeMillis();
        Long previous = clickCooldowns.put(key, now);
        return previous != null && now - previous < 250L;
    }

    private void dropReserve(Block block, long reserveBlocks) {
        long remaining = Math.max(0, reserveBlocks);
        while (remaining > 0) {
            int amount = (int) Math.min(64, remaining);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.DIAMOND_BLOCK, amount));
            remaining -= amount;
        }
    }
}

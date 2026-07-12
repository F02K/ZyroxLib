package de.tecca.zyrox.villagershop.listener;

import de.tecca.zyrox.villagershop.gui.VillagerShopEditorHolder;
import de.tecca.zyrox.villagershop.gui.VillagerShopGui;
import de.tecca.zyrox.villagershop.plugin.ZyroxVillagerShopPlugin;
import de.tecca.zyrox.villagershop.service.VillagerShopService;
import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import de.tecca.zyrox.villagershop.util.ItemStacks;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class VillagerShopListener implements Listener {

    private final ZyroxVillagerShopPlugin plugin;
    private final VillagerShopService shops;

    public VillagerShopListener(ZyroxVillagerShopPlugin plugin, VillagerShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Optional<VillagerShopRecord> shop = shops.findByEntity(entity);
        if (shop.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission(plugin.adminPermission())) {
            event.setCancelled(true);
            player.openInventory(VillagerShopGui.editor(shop.get(), plugin.text()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof VillagerShopEditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission(plugin.adminPermission())) {
            player.closeInventory();
            player.sendMessage(plugin.text().message("no-admin-permission", Map.of()));
            return;
        }
        handleEditorClick(player, event.getRawSlot(), event.getInventory(), holder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (shops.managedShopId(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (shops.managedShopId(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerRestock(VillagerReplenishTradeEvent event) {
        if (shops.managedShopId(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTrade(PlayerTradeEvent event) {
        Optional<VillagerShopRecord> shop = shops.findByEntity(event.getVillager());
        if (shop.isEmpty()) {
            return;
        }
        Optional<VillagerTradeRecord> trade = shops.matchingTrade(shop.get(), event.getTrade());
        if (trade.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        event.setRewardExp(false);
        if (trade.get().unlimited()) {
            event.setIncreaseTradeUses(false);
        } else if (trade.get().currentUses() >= trade.get().maxUses()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterPlayerTrade(PlayerTradeEvent event) {
        Optional<VillagerShopRecord> shop = shops.findByEntity(event.getVillager());
        if (shop.isEmpty()) {
            return;
        }
        shops.matchingTrade(shop.get(), event.getTrade())
                .ifPresent(trade -> shops.recordCompletedTrade(shop.get(), trade));
    }

    private void handleEditorClick(Player player, int rawSlot, Inventory inventory, VillagerShopEditorHolder holder) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            return;
        }

        if (rawSlot == VillagerShopGui.SLOT_TRADE_PREVIOUS) {
            holder.selectPrevious();
        } else if (rawSlot == VillagerShopGui.SLOT_TRADE_NEXT) {
            holder.selectNext();
        } else if (rawSlot == VillagerShopGui.SLOT_TRADE_ADD) {
            addTrade(player, inventory, holder);
            return;
        } else if (rawSlot == VillagerShopGui.SLOT_TRADE_REMOVE) {
            removeTrade(player, inventory, holder);
            return;
        } else if (rawSlot == VillagerShopGui.SLOT_INPUT_ONE) {
            holder.inputOne(copyMainHandOrNull(player));
        } else if (rawSlot == VillagerShopGui.SLOT_INPUT_TWO) {
            holder.inputTwo(copyMainHandOrNull(player));
        } else if (rawSlot == VillagerShopGui.SLOT_OUTPUT) {
            holder.output(copyMainHandOrNull(player));
        } else if (rawSlot == VillagerShopGui.SLOT_PROFESSION) {
            holder.profession(shops.nextProfession(holder.profession()));
        } else if (rawSlot == VillagerShopGui.SLOT_USES_DOWN) {
            holder.maxUses(Math.max(1, holder.maxUses() - 1));
        } else if (rawSlot == VillagerShopGui.SLOT_USES_UP) {
            holder.maxUses(holder.maxUses() + 1);
        } else if (rawSlot == VillagerShopGui.SLOT_UNLIMITED) {
            holder.unlimited(!holder.unlimited());
        } else if (rawSlot == VillagerShopGui.SLOT_RESET_USES) {
            resetUses(player, inventory, holder);
            return;
        } else if (rawSlot == VillagerShopGui.SLOT_SAVE) {
            save(player, inventory, holder);
            return;
        } else if (rawSlot == VillagerShopGui.SLOT_DELETE) {
            delete(player, holder);
            return;
        }

        VillagerShopGui.renderEditor(inventory, holder, plugin.text());
    }

    private ItemStack copyMainHandOrNull(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return null;
        }
        return ItemStacks.tradeItem(hand);
    }

    private void addTrade(Player player, Inventory inventory, VillagerShopEditorHolder holder) {
        try {
            VillagerShopRecord updated = shops.addTrade(holder.shop());
            holder.update(updated, updated.trades().size() - 1);
            VillagerShopGui.renderEditor(inventory, holder, plugin.text());
            player.sendMessage(plugin.text().message("trade-add-success", plugin.text().placeholders(updated)));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not add villager shop trade: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void removeTrade(Player player, Inventory inventory, VillagerShopEditorHolder holder) {
        if (holder.shop().trades().size() <= 1) {
            player.sendMessage(plugin.text().message("trade-remove-last", plugin.text().placeholders(holder.shop())));
            return;
        }
        try {
            VillagerShopRecord updated = shops.removeTrade(holder.selectedTrade().id());
            holder.update(updated, Math.min(holder.selectedIndex(), updated.trades().size() - 1));
            VillagerShopGui.renderEditor(inventory, holder, plugin.text());
            player.sendMessage(plugin.text().message("trade-remove-success", plugin.text().placeholders(updated)));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not remove villager shop trade: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void resetUses(Player player, Inventory inventory, VillagerShopEditorHolder holder) {
        try {
            VillagerShopRecord updated = shops.resetUses(holder.selectedTrade().id());
            holder.update(updated);
            VillagerShopGui.renderEditor(inventory, holder, plugin.text());
            player.sendMessage(plugin.text().message("reset-uses-success", plugin.text().placeholders(updated)));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not reset villager shop uses: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void save(Player player, Inventory inventory, VillagerShopEditorHolder holder) {
        if (holder.inputOne() == null || holder.output() == null) {
            player.sendMessage(plugin.text().message("invalid-trade", Map.of()));
            return;
        }
        try {
            VillagerShopRecord updated = shops.updateTrade(
                    holder.shop().id(),
                    holder.selectedTrade().id(),
                    holder.profession(),
                    holder.inputOne(),
                    holder.inputTwo(),
                    holder.output(),
                    holder.maxUses(),
                    holder.currentUses(),
                    holder.unlimited()
            );
            holder.update(updated);
            VillagerShopGui.renderEditor(inventory, holder, plugin.text());
            player.sendMessage(plugin.text().message("update-success", plugin.text().placeholders(updated)));
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not update villager shop: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void delete(Player player, VillagerShopEditorHolder holder) {
        try {
            shops.remove(holder.shop().id());
            player.closeInventory();
            player.sendMessage(plugin.text().message("remove-success", plugin.text().placeholders(holder.shop())));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not delete villager shop: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }
}

package de.tecca.zyrox.enchants.listener;

import de.tecca.zyrox.enchants.plugin.ZyroxEnchantsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public final class ZyroxEnchantAnvilListener implements Listener {

    private final ZyroxEnchantsPlugin plugin;

    public ZyroxEnchantAnvilListener(ZyroxEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();
        ItemStack currentResult = event.getResult();

        ItemStack result = plugin.enchants().combineAnvilResult(first, second, currentResult, plugin.maxLevel());
        event.setResult(result);
        if (currentResult == null && result != null && event.getView().getRepairCost() <= 0) {
            event.getView().setRepairCost(1);
        }
    }
}

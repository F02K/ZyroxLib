package de.tecca.zyrox.villagershop.storage;

import org.bukkit.inventory.ItemStack;

public interface VillagerShopItemCodec {

    String serialize(ItemStack item);

    ItemStack deserialize(String value);
}


package de.tecca.zyrox.shop.storage;

import org.bukkit.inventory.ItemStack;

public interface ShopItemCodec {

    String serialize(ItemStack item);

    ItemStack deserialize(String value);
}

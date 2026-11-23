package de.tecca.zyrox.shop.util;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopTextTest {

    @Test
    void guiListSkipsLinesThatBecomeBlankAfterPlaceholderReplacement() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.test.lines", List.of("First", "{optional}", "Last"));
        ShopText text = text(config);

        List<String> lines = text.guiList("test.lines", Map.of("optional", ""));

        assertEquals(List.of("First", "Last"), lines);
    }

    @Test
    void hologramLinesSkipBlankSpecialPlaceholderLines() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("holograms.lines", List.of("Shop #{shop_id}", "{enchant_lines}", "{empty_stock_lines}", "Lager: {stock}"));
        ShopText text = text(config);

        List<String> lines = text.hologramLines(shop());

        assertEquals(List.of("Shop #42", "Lager: 5"), lines);
    }

    private static ShopText text(YamlConfiguration config) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        ZyroxDiamondPaymentService payments = mock(ZyroxDiamondPaymentService.class);
        when(payments.formatDiamonds(anyLong())).thenAnswer(invocation -> invocation.getArgument(0) + " Diamant");
        return new ShopText(plugin, payments);
    }

    private static ShopRecord shop() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STICK);
        when(item.hasItemMeta()).thenReturn(false);
        return new ShopRecord(
                42,
                "world",
                1,
                2,
                3,
                UUID.nameUUIDFromBytes("Owner".getBytes()),
                "Owner",
                item,
                1,
                1,
                5,
                0,
                null,
                null,
                "2026-07-13T12:00:00Z",
                "2026-07-13T12:00:00Z"
        );
    }
}

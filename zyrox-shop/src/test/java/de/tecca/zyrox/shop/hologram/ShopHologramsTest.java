package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopHologramsTest {

    @AfterEach
    void resetRecordedDecentRemoval() {
        TestDecentApi.removedName = null;
    }

    @Test
    void noneBackendCreatesNoopService() {
        ShopHologramService service = ShopHolograms.create(plugin("none", false, List.of()), mock(ShopText.class));

        assertInstanceOf(NoopShopHologramService.class, service);
    }

    @Test
    void textDisplayBackendCreatesTextDisplayService() {
        ShopHologramService service = ShopHolograms.create(plugin("textdisplay", false, List.of()), mock(ShopText.class));

        assertInstanceOf(TextDisplayShopHologramService.class, service);
    }

    @Test
    void autoBackendWithoutDecentCreatesTextDisplayService() {
        ShopHologramService service = ShopHolograms.create(plugin("auto", false, List.of()), mock(ShopText.class));

        assertInstanceOf(TextDisplayShopHologramService.class, service);
    }

    @Test
    void decentBackendFallsBackToTextDisplayWhenDhapiIsMissing() {
        ShopHologramService service = ShopHolograms.create(plugin("decent", true, List.of()), mock(ShopText.class));

        assertInstanceOf(TextDisplayShopHologramService.class, service);
    }

    @Test
    void removesOnlyMatchingTextDisplayForShop() {
        TextDisplay matching = textDisplay(Set.of(
                ShopHologramCleanup.TEXT_DISPLAY_TAG,
                ShopHologramCleanup.textDisplayShopIdTag(42)
        ));
        TextDisplay otherShop = textDisplay(Set.of(
                ShopHologramCleanup.TEXT_DISPLAY_TAG,
                ShopHologramCleanup.textDisplayShopIdTag(7)
        ));
        TextDisplay unrelated = textDisplay(Set.of("other-plugin"));
        World world = mock(World.class);
        when(world.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of(matching, otherShop, unrelated));

        ShopHologramCleanup.removeTextDisplay(plugin("auto", false, List.of(world)), 42);

        verify(matching).remove();
        verify(otherShop, never()).remove();
        verify(unrelated, never()).remove();
    }

    @Test
    void removesAllTaggedTextDisplays() {
        TextDisplay tagged = textDisplay(Set.of(ShopHologramCleanup.TEXT_DISPLAY_TAG));
        TextDisplay unrelated = textDisplay(Set.of("other-plugin"));
        World world = mock(World.class);
        when(world.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of(tagged, unrelated));

        ShopHologramCleanup.removeTextDisplays(plugin("auto", false, List.of(world)));

        verify(tagged).remove();
        verify(unrelated, never()).remove();
    }

    @Test
    void removesDecentHologramByStableShopName() throws Exception {
        JavaPlugin plugin = plugin("auto", true, List.of());
        Method remove = TestDecentApi.class.getMethod("removeHologram", String.class);

        ShopHologramCleanup.removeDecentHologram(plugin, remove, 42);

        assertEquals("zyrox_shop_42", TestDecentApi.removedName);
    }

    private static JavaPlugin plugin(String backend, boolean decentLoaded, List<World> worlds) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("holograms.backend", backend);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ShopHologramsTest"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getWorlds()).thenReturn(worlds);
        when(pluginManager.isPluginEnabled("DecentHolograms")).thenReturn(decentLoaded);
        return plugin;
    }

    private static TextDisplay textDisplay(Set<String> tags) {
        TextDisplay display = mock(TextDisplay.class);
        when(display.getScoreboardTags()).thenReturn(tags);
        return display;
    }

    public static final class TestDecentApi {

        private static String removedName;

        public static void removeHologram(String name) {
            removedName = name;
        }
    }
}

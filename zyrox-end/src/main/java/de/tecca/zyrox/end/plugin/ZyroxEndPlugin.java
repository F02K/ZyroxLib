package de.tecca.zyrox.end.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.end.command.ZyroxEndCommand;
import de.tecca.zyrox.end.fight.EndFightService;
import de.tecca.zyrox.end.listener.EndFightListener;
import de.tecca.zyrox.end.listener.EndDragonEggListener;
import de.tecca.zyrox.end.service.DragonEggStateStorage;
import de.tecca.zyrox.end.service.EndDragonEggService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class ZyroxEndPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-end";

    private ZyroxApi zyroxApi;
    private EndDragonEggService dragonEggService;
    private EndFightService fightService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        zyroxApi = loadZyroxApi();
        if (zyroxApi == null) {
            getLogger().severe("ZyroxAPI is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dragonEggService = new EndDragonEggService(this, new DragonEggStateStorage(stateFile(), getLogger()));
        fightService = new EndFightService(this, dragonEggService);

        registerCommand();
        registerListeners();
        registerZyroxApiIntegrations();
        fightService.start();

        getLogger().info("ZyroxEnd is enabled.");
    }

    @Override
    public void onDisable() {
        if (fightService != null) {
            fightService.shutdown();
        }

        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }

        getLogger().info("ZyroxEnd is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        dragonEggService.reload();
        fightService.reloadConfig();
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.end.admin");
    }

    public EndDragonEggService dragonEggService() {
        return dragonEggService;
    }

    public EndFightService fightService() {
        return fightService;
    }

    private void registerCommand() {
        PluginCommand command = getCommand("end");
        if (command == null) {
            getLogger().warning("Command /end is missing from plugin.yml.");
            return;
        }

        ZyroxEndCommand executor = new ZyroxEndCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new EndDragonEggListener(this, dragonEggService), this);
        getServer().getPluginManager().registerEvents(new EndFightListener(fightService), this);
    }

    private void registerZyroxApiIntegrations() {
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxEnd",
                getDescription().getVersion(),
                "First Ender Dragon Egg ownership tagging for ZyroxMC.",
                adminPermission()
        ));

        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload End",
                "Reloads ZyroxEnd config and claim state.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxEnd reloaded.");
                }
        ));
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        zyroxApi.modules().unregister(MODULE_ID);
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private File stateFile() {
        return new File(getDataFolder(), "state.yml");
    }
}

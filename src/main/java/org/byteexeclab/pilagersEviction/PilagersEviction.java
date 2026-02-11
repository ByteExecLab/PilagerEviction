package org.byteexeclab.pilagersEviction;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.listeners.ChestLootListener;
import org.byteexeclab.pilagersEviction.listeners.DestroyedOutpostListener;
import org.byteexeclab.pilagersEviction.listeners.OutpostMarkerListener;
import org.byteexeclab.pilagersEviction.listeners.PillagerSpawnBlocker;
import org.byteexeclab.pilagersEviction.redis.RedisStore;
import org.byteexeclab.pilagersEviction.service.OutpostService;

import java.util.*;

public final class PilagersEviction extends JavaPlugin {
    private RedisStore redisStore;
    private OutpostService outpostService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.redisStore = new RedisStore(this);
        this.redisStore.connect();

        this.outpostService = new OutpostService(this, redisStore);
        this.outpostService.loadCacheFromRedis();
        this.outpostService.reloadBlockedReasons();

        var admin = new org.byteexeclab.pilagersEviction.commands.AdminCommands(this, outpostService);
        var cmd = getCommand("pev");
        if (cmd != null) {
            cmd.setExecutor(admin);
            cmd.setTabCompleter(admin);
        } else {
            getLogger().severe("Command 'pev' not found. Check plugin.yml!");
        }

        boolean chestEnabled = getConfig().getBoolean("clearTriggers.chestLooted.enabled", true);
        boolean destroyedEnabled = getConfig().getBoolean("clearTriggers.destroyed.enabled", true);

        if (chestEnabled) {
            Bukkit.getPluginManager().registerEvents(new ChestLootListener(this, outpostService), this);
        }
        if (destroyedEnabled) {
            Bukkit.getPluginManager().registerEvents(new DestroyedOutpostListener(this, outpostService), this);
        }
        Bukkit.getPluginManager().registerEvents((Listener) new PillagerSpawnBlocker(outpostService), this);

        getServer().getPluginManager().registerEvents(
                new OutpostMarkerListener(outpostService),
                this
        );

        getLogger().info("ClearedOutposts enabled.");
    }

    @Override
    public void onDisable() {
        try {
            if (redisStore != null) redisStore.close();
        } catch (Exception ignored) {}
    }
}

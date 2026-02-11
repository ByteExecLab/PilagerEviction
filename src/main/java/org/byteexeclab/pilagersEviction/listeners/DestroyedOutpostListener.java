package org.byteexeclab.pilagersEviction.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.service.OutpostService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DestroyedOutpostListener implements Listener {
    private final JavaPlugin plugin;
    private final OutpostService service;

    // debounce tasks per zoneId
    private final Map<String, Integer> scheduledTaskIds = new ConcurrentHashMap<>();

    public DestroyedOutpostListener(JavaPlugin plugin, OutpostService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();

        Location breakLoc = e.getBlock().getLocation();
        World w = breakLoc.getWorld();
        if (w == null) return;

        Location outpost = service.findNearestOutpostCenter(breakLoc, 256);
        if (outpost == null) return;

        // only react if breaking is close to that outpost
        int dx = breakLoc.getBlockX() - outpost.getBlockX();
        int dz = breakLoc.getBlockZ() - outpost.getBlockZ();
        if (dx * dx + dz * dz > (100 * 100)) return;

        String zoneId = service.zoneIdForOutpost(outpost);
        if (service.isAlreadyCleared(zoneId)) return;

        // Debounce: cancel prior scheduled scan and schedule a new one
        Integer oldTask = scheduledTaskIds.remove(zoneId);
        if (oldTask != null) plugin.getServer().getScheduler().cancelTask(oldTask);

        int delayTicks = service.destroyedDebounceSeconds() * 20;
        int taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            scheduledTaskIds.remove(zoneId);

            if (service.isAlreadyCleared(zoneId)) return;

            if (!service.destroyedVerifyEnabled()) {
                service.markCleared(outpost, p);
                return;
            }

            int remaining = service.countSignatureBlocks(w, outpost);
            if (remaining <= service.remainingThreshold()) {
                service.markCleared(outpost, p);
            }
        }, delayTicks);

        scheduledTaskIds.put(zoneId, taskId);
    }
}

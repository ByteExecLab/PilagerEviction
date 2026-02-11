package org.byteexeclab.pilagersEviction.listeners;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.byteexeclab.pilagersEviction.service.OutpostService;

public class PillagerSpawnBlocker implements Listener {
    private final OutpostService service;

    public PillagerSpawnBlocker(OutpostService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.PILLAGER) return;

        if (service.ignoreRaids() && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.RAID) return;

        if (!service.blockSpawnReasons().contains(e.getSpawnReason())) return;

        if (service.isLocationInClearedZone(e.getLocation())) {
            e.setCancelled(true);
        }
    }
}

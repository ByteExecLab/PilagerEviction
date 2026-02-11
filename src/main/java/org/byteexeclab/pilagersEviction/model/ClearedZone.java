package org.byteexeclab.pilagersEviction.model;

import org.bukkit.Location;

import java.util.UUID;

public record ClearedZone(UUID worldId, int x, int y, int z, long clearedAtEpochMs) {
    public Location toLocation(org.bukkit.World world) {
        return new Location(world, x, y, z);
    }
}

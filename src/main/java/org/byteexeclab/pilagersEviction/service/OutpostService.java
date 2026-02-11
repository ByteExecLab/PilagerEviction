package org.byteexeclab.pilagersEviction.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.model.ClearedZone;
import org.byteexeclab.pilagersEviction.redis.RedisStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OutpostService {
    private final JavaPlugin plugin;
    private final RedisStore redis;

    // in-memory cache of cleared zones keyed by zoneId
    private final Map<String, ClearedZone> clearedZones = new ConcurrentHashMap<>();

    public OutpostService(JavaPlugin plugin, RedisStore redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    public void loadCacheFromRedis() {
        if (!redis.isEnabled()) return;
        try {
            Map<String, Map<String, String>> all = redis.loadAllZones();
            int loaded = 0;
            for (Map.Entry<String, Map<String, String>> e : all.entrySet()) {
                ClearedZone zone = parseZone(e.getValue());
                if (zone != null) {
                    clearedZones.put(e.getKey(), zone);
                    loaded++;
                }
            }
            plugin.getLogger().info("Loaded " + loaded + " cleared outpost zone(s) from Redis.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed loading zones from Redis: " + ex.getMessage());
        }
    }

    private ClearedZone parseZone(Map<String, String> h) {
        try {
            UUID world = UUID.fromString(h.get("world"));
            int x = Integer.parseInt(h.get("x"));
            int y = Integer.parseInt(h.get("y"));
            int z = Integer.parseInt(h.get("z"));
            long clearedAt = Long.parseLong(h.getOrDefault("clearedAt", String.valueOf(System.currentTimeMillis())));
            return new ClearedZone(world, x, y, z, clearedAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    public FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public int preventionRadius() {
        return Math.max(8, cfg().getInt("prevention.radius", 72));
    }

    public boolean ignoreRaids() {
        return cfg().getBoolean("prevention.ignoreRaids", true);
    }

    public Set<String> blockSpawnReasons() {
        return new HashSet<>(cfg().getStringList("prevention.blockSpawnReasons"));
    }

    public boolean isLocationInClearedZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        UUID wid = loc.getWorld().getUID();
        int r = preventionRadius();
        int r2 = r * r;

        // 2D check (fast + good enough)
        int lx = loc.getBlockX();
        int lz = loc.getBlockZ();
        for (ClearedZone z : clearedZones.values()) {
            if (!z.worldId().equals(wid)) continue;
            int dx = lx - z.x();
            int dz = lz - z.z();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    /**
     * Finds nearest outpost center-ish using locateNearestStructure.
     */
    public Location findNearestOutpostCenter(Location near, int searchRadiusBlocks) {
        if (near == null || near.getWorld() == null) return null;
        World w = near.getWorld();
        try {
            // Paper supports this well; returns null if not found
            return w.locateNearestStructure(near, StructureType.PILLAGER_OUTPOST, searchRadiusBlocks, false);
        } catch (Throwable t) {
            plugin.getLogger().warning("locateNearestStructure not available/failed: " + t.getMessage());
            return null;
        }
    }

    public String zoneIdForOutpost(Location outpostCenter) {
        // stable-ish: use chunk coords
        int cx = outpostCenter.getBlockX() >> 4;
        int cz = outpostCenter.getBlockZ() >> 4;
        return outpostCenter.getWorld().getUID() + ":" + cx + ":" + cz;
    }

    public boolean isAlreadyCleared(String zoneId) {
        return clearedZones.containsKey(zoneId);
    }

    public void markCleared(Location outpostCenter, Player byPlayerOrNull) {
        if (outpostCenter == null || outpostCenter.getWorld() == null) return;
        String zoneId = zoneIdForOutpost(outpostCenter);
        if (isAlreadyCleared(zoneId)) return;

        ClearedZone zone = new ClearedZone(
                outpostCenter.getWorld().getUID(),
                outpostCenter.getBlockX(),
                outpostCenter.getBlockY(),
                outpostCenter.getBlockZ(),
                System.currentTimeMillis()
        );

        clearedZones.put(zoneId, zone);

        if (redis.isEnabled()) {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("world", zone.worldId().toString());
                fields.put("x", String.valueOf(zone.x()));
                fields.put("y", String.valueOf(zone.y()));
                fields.put("z", String.valueOf(zone.z()));
                fields.put("clearedAt", String.valueOf(zone.clearedAtEpochMs()));
                if (byPlayerOrNull != null) fields.put("clearedBy", byPlayerOrNull.getUniqueId().toString());
                redis.saveZone(zoneId, fields);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed saving cleared zone to Redis: " + e.getMessage());
            }
        }

        plugin.getLogger().info("Marked outpost as cleared: " + zoneId +
                " @ " + outpostCenter.getWorld().getName() + " " +
                outpostCenter.getBlockX() + "," + outpostCenter.getBlockY() + "," + outpostCenter.getBlockZ());
    }

    // ---------- Destroy verification scan ----------
    public boolean destroyedVerifyEnabled() {
        return cfg().getBoolean("clearTriggers.destroyed.verifyByBlockScan", true);
    }

    public int destroyedDebounceSeconds() {
        return Math.max(1, cfg().getInt("clearTriggers.destroyed.debounceSeconds", 3));
    }

    public int scanRadius() {
        return Math.max(8, cfg().getInt("clearTriggers.destroyed.scan.radius", 48));
    }

    public int remainingThreshold() {
        return Math.max(0, cfg().getInt("clearTriggers.destroyed.scan.remainingThreshold", 120));
    }

    public Set<Material> signatureBlocks() {
        List<String> names = cfg().getStringList("clearTriggers.destroyed.scan.signatureBlocks");
        Set<Material> mats = EnumSet.noneOf(Material.class);
        for (String n : names) {
            try {
                mats.add(Material.valueOf(n));
            } catch (Exception ignored) {}
        }
        return mats;
    }

    /**
     * Counts remaining signature blocks in a cube around center.
     * This is a heuristic, but works well when tuned.
     */
    public int countSignatureBlocks(World w, Location center) {
        int r = scanRadius();
        Set<Material> sig = signatureBlocks();
        if (sig.isEmpty()) return Integer.MAX_VALUE; // avoid false-clears

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int minY = Math.max(w.getMinHeight(), cy - r);
        int maxY = Math.min(w.getMaxHeight() - 1, cy + r);

        int count = 0;

        // simple scan; you can optimize later (chunk/bucket), but keep it correct first
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (sig.contains(b.getType())) {
                        count++;
                        if (count > remainingThreshold()) return count; // early exit
                    }
                }
            }
        }
        return count;
    }
}

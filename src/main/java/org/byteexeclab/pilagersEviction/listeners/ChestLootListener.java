package org.byteexeclab.pilagersEviction.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.service.OutpostService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChestLootListener implements Listener {

    private final JavaPlugin plugin;
    private final OutpostService service;

    private final boolean requireItemTaken;

    private final int maxChestDistanceSq = 80*80;   // Must be near the outpost center
    private final int maxChunkDelta = 1;            // Chest must be in the same chunk +/-1
    private final boolean tryLootTableCheck = true;

    private final Map<UUID, ChestKey> openedChest = new ConcurrentHashMap<>();


    public ChestLootListener(JavaPlugin plugin, OutpostService service) {
        this.plugin = plugin;
        this.service = service;
        this.requireItemTaken = plugin.getConfig().getBoolean("clearTriggers.chestLooted.requireItemTaken", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;

        // Remember which chest player has opened
        openedChest.put(p.getUniqueId(), ChestKey.from(chest.getLocation()));

        if (!requireItemTaken) {
            // “Opening clears” mode (still filtered by near-outpost check in tryClearFromChest)
            tryClearFromChest(chest.getLocation(), p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        openedChest.remove(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!requireItemTaken) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Only when clicking TOP inventory (the chest), taking something
        if (e.getClickedInventory() == null) return;
        if (e.getView().getTopInventory() != e.getClickedInventory()) return;

        InventoryHolder holder = e.getClickedInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;

        // Must be the same chest player opened
        ChestKey key = openedChest.get(p.getUniqueId());
        if (key == null || !key.equals(ChestKey.from(chest.getLocation()))) return;

        if (!isTakingFromTopInventory(e.getAction())) return;

        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        if (tryLootTableCheck && hasLootTable(chest)) {
            if (!isOutpostLootTable(chest)) return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> tryClearFromChest(chest.getLocation(), p));
    }

    private boolean isTakingFromTopInventory(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_SWAP, COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }

    private void tryClearFromChest(Location chestLoc, Player p) {
        World w = chestLoc.getWorld();
        if (w == null) return;

        Location outpost = service.findNearestOutpostCenter(chestLoc, 256);
        if (outpost == null) return;
        if (outpost.getWorld() != w) return;

        // must be close to that outpost
        int dx = chestLoc.getBlockX() - outpost.getBlockX();
        int dz = chestLoc.getBlockZ() - outpost.getBlockZ();
        if (dx * dx + dz * dz > maxChestDistanceSq) return;

        // and roughly within same outpost chunk region (reduces “player chest near outpost” false clears)
        int chestCX = chestLoc.getBlockX() >> 4;
        int chestCZ = chestLoc.getBlockZ() >> 4;
        int outCX = outpost.getBlockX() >> 4;
        int outCZ = outpost.getBlockZ() >> 4;
        if (Math.abs(chestCX - outCX) > maxChunkDelta || Math.abs(chestCZ - outCZ) > maxChunkDelta) return;

        service.markCleared(outpost, p);
    }

    private boolean hasLootTable(Chest chest) {
        try {
            return chest.getLootTable() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isOutpostLootTable(Chest chest) {
        LootTable table = chest.getLootTable();
        if (table == null || table.getKey() == null) return false;
        return table.getKey().toString().equals("minecraft:chests/pillager_outpost");
    }

    private record ChestKey(UUID world, int x, int y, int z) {
        static ChestKey from(Location loc) {
            return new ChestKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }
}

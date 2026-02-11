package org.byteexeclab.pilagersEviction.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.service.OutpostService;

public class ChestLootListener implements Listener {
    private final JavaPlugin plugin;
    private final OutpostService service;
    private final boolean requireItemTaken;

    public ChestLootListener(JavaPlugin plugin, OutpostService service) {
        this.plugin = plugin;
        this.service = service;
        this.requireItemTaken = plugin.getConfig().getBoolean("clearTriggers.chestLooted.requireItemTaken", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (requireItemTaken) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;

        tryClearFromChest(chest.getLocation(), p);
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

        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        // If they are taking items out of the chest into player inv or cursor
        // A simple heuristic: any click that removes an item from the top inv.
        // (Good enough for “looted”.)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // after event, verify item reduced in chest slot
            // We won't overcomplicate: just clear on first valid take attempt.
            tryClearFromChest(chest.getLocation(), p);
        });
    }

    private void tryClearFromChest(Location chestLoc, Player p) {
        World w = chestLoc.getWorld();
        if (w == null) return;

        Location outpost = service.findNearestOutpostCenter(chestLoc, 256);
        if (outpost == null) return;

        // Make sure chest is reasonably near that outpost
        if (outpost.getWorld() != w) return;
        int dx = chestLoc.getBlockX() - outpost.getBlockX();
        int dz = chestLoc.getBlockZ() - outpost.getBlockZ();
        if (dx * dx + dz * dz > (80 * 80)) return; // only count if chest is near the outpost

        service.markCleared(outpost, p);
    }
}

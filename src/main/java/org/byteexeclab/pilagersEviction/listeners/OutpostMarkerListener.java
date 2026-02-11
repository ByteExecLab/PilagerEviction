package org.byteexeclab.pilagersEviction.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.byteexeclab.pilagersEviction.service.OutpostService; // adjust package if needed

public class OutpostMarkerListener implements Listener {

    private final OutpostService outpostService;

    public OutpostMarkerListener(OutpostService outpostService) {
        this.outpostService = outpostService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("pilagerseviction.admin")) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHER_STAR) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!"Outpost Marker".equalsIgnoreCase(name)) return;

        Location outpost = outpostService.findNearestOutpostCenter(p.getLocation(), 512);
        if (outpost == null) {
            p.sendMessage(ChatColor.RED + "No outpost found nearby.");
            return;
        }

        outpostService.markCleared(outpost, p);
        p.sendMessage(ChatColor.GREEN + "Marked nearest outpost as cleared.");
        e.setCancelled(true);
    }
}

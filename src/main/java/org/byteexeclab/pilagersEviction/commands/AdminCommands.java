package org.byteexeclab.pilagersEviction.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.byteexeclab.pilagersEviction.service.OutpostService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminCommands implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final OutpostService service;
    private final NamespacedKey markerKey;

    public AdminCommands(JavaPlugin plugin, OutpostService service) {
        this.plugin = plugin;
        this.service = service;
        this.markerKey = new NamespacedKey(plugin, "outpost_marker");
    }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("pilagerseviction.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "reload" -> cmdReload(sender);
            case "info" -> cmdInfo(sender);
            case "mark" -> cmdMark(sender, args);
            case "unmark" -> cmdUnmark(sender, args);
            case "list" -> cmdList(sender, args);
            case "marker" -> cmdMarker(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /pev help");
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "PilagersEviction Admin Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/pev info" + ChatColor.GRAY + " - show nearest outpost and cleared status");
        sender.sendMessage(ChatColor.YELLOW + "/pev mark [searchRadius]" + ChatColor.GRAY + " - mark nearest outpost as cleared");
        sender.sendMessage(ChatColor.YELLOW + "/pev unmark nearest" + ChatColor.GRAY + " - unmark nearest outpost");
        sender.sendMessage(ChatColor.YELLOW + "/pev unmark id <zoneId>" + ChatColor.GRAY + " - unmark by id");
        sender.sendMessage(ChatColor.YELLOW + "/pev list [page]" + ChatColor.GRAY + " - list cleared outposts");
        sender.sendMessage(ChatColor.YELLOW + "/pev marker give [player]" + ChatColor.GRAY + " - give outpost marker item");
        sender.sendMessage(ChatColor.YELLOW + "/pev reload" + ChatColor.GRAY + " - reload config");
    }

    private boolean cmdReload(CommandSender sender) {
        plugin.reloadConfig();
        service.reloadFromConfig();
        sender.sendMessage(ChatColor.GREEN + "PilagersEviction config reloaded.");
        return true;
    }

    private boolean cmdInfo(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Location outpost = service.findNearestOutpostCenter(p.getLocation(), 1024);
        if (outpost == null) {
            p.sendMessage(ChatColor.RED + "No Pillager Outpost found within 1024 blocks.");
            return true;
        }

        String zoneId = service.zoneIdForOutpost(outpost);
        boolean cleared = service.isAlreadyCleared(zoneId);

        int dx = p.getLocation().getBlockX() - outpost.getBlockX();
        int dz = p.getLocation().getBlockZ() - outpost.getBlockZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);

        p.sendMessage(ChatColor.GOLD + "Nearest Outpost:");
        p.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + outpost.getWorld().getName());
        p.sendMessage(ChatColor.GRAY + "Center: " + ChatColor.WHITE + outpost.getBlockX() + ", " + outpost.getBlockY() + ", " + outpost.getBlockZ());
        p.sendMessage(ChatColor.GRAY + "Distance: " + ChatColor.WHITE + dist + " blocks");
        p.sendMessage(ChatColor.GRAY + "ZoneId: " + ChatColor.WHITE + zoneId);
        p.sendMessage(ChatColor.GRAY + "Cleared: " + (cleared ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        return true;
    }

    private boolean cmdMark(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        int search = 1024;
        if (args.length >= 2) {
            try { search = Math.max(64, Integer.parseInt(args[1])); } catch (Exception ignored) {}
        }

        Location outpost = service.findNearestOutpostCenter(p.getLocation(), search);
        if (outpost == null) {
            p.sendMessage(ChatColor.RED + "No Pillager Outpost found within " + search + " blocks.");
            return true;
        }

        String zoneId = service.zoneIdForOutpost(outpost);
        if (service.isAlreadyCleared(zoneId)) {
            p.sendMessage(ChatColor.YELLOW + "That outpost is already marked cleared: " + zoneId);
            return true;
        }

        service.markCleared(outpost, p);
        p.sendMessage(ChatColor.GREEN + "Marked nearest outpost as cleared: " + zoneId);
        return true;
    }

    private boolean cmdUnmark(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /pev unmark nearest OR /pev unmark id <zoneId>");
            return true;
        }

        if (args[1].equalsIgnoreCase("nearest")) {
            Location outpost = service.findNearestOutpostCenter(p.getLocation(), 1024);
            if (outpost == null) {
                p.sendMessage(ChatColor.RED + "No outpost found nearby.");
                return true;
            }
            String zoneId = service.zoneIdForOutpost(outpost);
            if (!service.isAlreadyCleared(zoneId)) {
                p.sendMessage(ChatColor.YELLOW + "Nearest outpost is not marked cleared.");
                return true;
            }
            service.unmark(zoneId);
            p.sendMessage(ChatColor.GREEN + "Unmarked cleared outpost: " + zoneId);
            return true;
        }

        if (args[1].equalsIgnoreCase("id") && args.length >= 3) {
            String zoneId = args[2];
            if (!service.isAlreadyCleared(zoneId)) {
                p.sendMessage(ChatColor.YELLOW + "That zoneId is not currently cleared: " + zoneId);
                return true;
            }
            service.unmark(zoneId);
            p.sendMessage(ChatColor.GREEN + "Unmarked cleared outpost: " + zoneId);
            return true;
        }

        p.sendMessage(ChatColor.RED + "Usage: /pev unmark nearest OR /pev unmark id <zoneId>");
        return true;
    }

    private boolean cmdList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {}
        }

        List<String> ids = new ArrayList<>(service.getClearedZoneIds());
        ids.sort(String::compareToIgnoreCase);

        int perPage = 10;
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
        page = Math.min(page, pages);

        int from = (page - 1) * perPage;
        int to = Math.min(ids.size(), from + perPage);

        sender.sendMessage(ChatColor.GOLD + "Cleared Outposts (" + ids.size() + ") Page " + page + "/" + pages + ":");
        for (int i = from; i < to; i++) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + ids.get(i));
        }
        return true;
    }

    private boolean cmdMarker(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Usage: /pev marker give [player]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /pev marker give <player>");
                return true;
            }
            target = p;
        }

        ItemStack marker = service.createMarkerItem(markerKey);
        target.getInventory().addItem(marker);
        sender.sendMessage(ChatColor.GREEN + "Gave Outpost Marker to " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pilagerseviction.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("help", "info", "mark", "unmark", "list", "marker", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unmark")) {
            return filter(List.of("nearest", "id"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("marker")) {
            return filter(List.of("give"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("marker") && args[1].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("unmark") && args[1].equalsIgnoreCase("id")) {
            return filter(new ArrayList<>(service.getClearedZoneIds()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted()
                .collect(Collectors.toList());
    }
}

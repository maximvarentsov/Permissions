package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

/**
 * Listen for player-based events to keep track of players and build permissions.
 */
class Listeners implements Listener {

    private final Permissions plugin;

    public Listeners(final Permissions plugin) {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        plugin.getManager().calculateAttachment(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onPlayerLogin(final PlayerJoinEvent event) {
        plugin.getManager().registerPlayer(event.getPlayer());
        if (plugin.configLoadError && event.getPlayer().hasPermission("permissions.reload")) {
            plugin.configLoadError = false;
            event.getPlayer().sendMessage(ChatColor.RED + "[" + ChatColor.GREEN + "PermissionsBukkit" + ChatColor.RED + "] Your configuration is invalid, see the console for details.");
        }
    }

    // Unregister players when needed

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onPlayerKick(final PlayerKickEvent event) {
        plugin.getManager().unregisterPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    public void onPlayerQuit(final PlayerQuitEvent event) {
        plugin.getManager().unregisterPlayer(event.getPlayer());
    }

    // Prevent doing things in the event of permissions.build: false

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!event.getPlayer().hasPermission("permissions.build")) {
            bother(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!event.getPlayer().hasPermission("permissions.build")) {
            bother(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onBlockBreak(final BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission("permissions.build")) {
            bother(event.getPlayer());
            event.setCancelled(true);
        }
    }

    private void bother(final Player player) {
        if (plugin.getConfig().getString("messages/build", "").length() > 0) {
            String message = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages/build", ""));
            player.sendMessage(message);
        }
    }
}

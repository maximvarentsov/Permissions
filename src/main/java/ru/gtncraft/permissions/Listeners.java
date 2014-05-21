package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listen for player-based events to keep track of players and build permissions.
 */
class Listeners implements Listener {

    final Permissions plugin;

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
}

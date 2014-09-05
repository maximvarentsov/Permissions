package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

final class Listeners implements Listener {

    private final String format;
    private final PermissionManager manager;

    public Listeners(final Permissions plugin) {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        manager = plugin.getManager();
        format = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("format"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    void onPlayerLogin(final PlayerJoinEvent event) {
        manager.registerPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    void onPlayerQuit(final PlayerQuitEvent event) {
        manager.unregisterPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    void onWorldChange(final PlayerChangedWorldEvent event) {
        manager.calculateAttachment(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("unused")
    void onPlayerChat(final AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        List<Group> groups = manager.getGroups(player.getName());
        Group group = groups.get(0);

        String message = format.replace("%name", "%1$s");
        message = message.replace("%message", "%2$s");
        message = message.replace("%prefix",  ChatColor.translateAlternateColorCodes('&',group.getPrefix()));

        event.setFormat(message);
    }
}

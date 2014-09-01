package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class Listeners implements Listener {

    private final PermissionManager manager;
    private final boolean registerOnJoin;

    public Listeners(final Permissions plugin) {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        manager = plugin.getManager();
        registerOnJoin = plugin.getConfig().getBoolean("registerOnJoin", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    void onPlayerLogin(final PlayerJoinEvent event) {
        if (registerOnJoin) {
            manager.registerPlayer(event.getPlayer());
        }
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

    /*@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("unused")
    void onPlayerChat(final AsyncPlayerChatEvent event) {
        List<Group> groups = manager.getPlayerInfo(event.getPlayer().getName()).getGroups();

    }*/
}

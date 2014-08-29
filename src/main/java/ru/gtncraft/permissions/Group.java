package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A class representing a permissions group.
 */
public final class Group {

    final PermissionManager manager;
    final String name;

    Group(final PermissionManager manager, final String name) {
        this.manager = manager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * @deprecated Use UUIDs instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public List<String> getPlayers() {
        List<String> result = new ArrayList<>();
        if (manager.getNode("users") != null) {
            for (String user : manager.getNode("users").getKeys(false)) {
                ConfigurationSection node = manager.getNode("users/" + user);
                for (String group : node.getStringList("groups")) {
                    if (name.equalsIgnoreCase(group) && !result.contains(user)) {
                        // attempt to determine the username
                        if (node.getString("name") != null) {
                            // converted node
                            result.add(node.getString("name"));
                        } else {
                            // unconverted node, or UUID node missing "name" element
                            result.add(user);
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unused")
    public List<UUID> getPlayerUUIDs() {
        List<UUID> result = new ArrayList<>();
        if (manager.getNode("users") != null) {
            for (String user : manager.getNode("users").getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(user);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                for (String group : manager.getNode("users/" + user).getStringList("groups")) {
                    if (name.equalsIgnoreCase(group) && !result.contains(uuid)) {
                        result.add(uuid);
                        break;
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unused")
    public List<Player> getOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : getPlayerUUIDs()) {
            Player player = Bukkit.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    @SuppressWarnings("unused")
    public PermissionInfo getInfo() {
        ConfigurationSection node = manager.getNode("groups/" + name);
        if (node != null) {
            return new PermissionInfo(manager, node, "inheritance");
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Group && name.equalsIgnoreCase(((Group) o).getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Group{name=" + name + "}";
    }
}

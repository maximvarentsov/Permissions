package ru.gtncraft.permissions;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A class representing a permissions group.
 */
public final class Group {

    private final PermissionManager manager;
    private final String name;

    Group(PermissionManager manager, String name) {
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
    public List<String> getPlayers() {
        ConfigurationSection users = manager.getNode("users");
        if (users == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String user : users.getKeys(false)) {
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
        return result;
    }

    public List<UUID> getPlayerUUIDs() {
        ConfigurationSection users = manager.getNode("users");
        if (users == null) {
            return Collections.emptyList();
        }
        List<UUID> result = new ArrayList<>();
        for (String user : users.getKeys(false)) {
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
        return result;
    }

    public PermissionInfo getInfo() {
        ConfigurationSection node = manager.getNode("groups/" + name);
        if (node != null) {
            return new PermissionInfo(manager, node, "inheritance");
        }
        return null;
    }

    public String getPrefix() {
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

package ru.gtncraft.permissions;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Group {

    private final PermissionManager manager;
    private final String name;
    private final String prefix;

    Group(PermissionManager manager, String name) {
        this.manager = manager;
        this.name = name;
        this.prefix = manager.getNode("groups/" + name).getString("prefix", name);
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
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

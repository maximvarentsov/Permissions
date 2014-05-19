package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A class representing a permissions group.
 */
public class Group {

    final PermissionManager manager;
    final String name;

    protected Group(final PermissionManager manager, final String name) {
        this.manager = manager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPlayers() {
        Set<String> result = new TreeSet<>();
        if (manager.getNode("users") != null) {
            manager.getNode("users")
                   .getKeys(false)
                   .forEach( user ->
                manager.getNode("users/" + user)
                       .getStringList("groups")
                       .stream()
                       .filter(name::equalsIgnoreCase)
                       .map(group -> user)
                       .forEach(result::add)
            );
        }
        return result;
    }

    public Set<Player> getOnlinePlayers() {
        return getPlayers().stream().map(user -> Bukkit.getServer().getPlayerExact(user)).collect(Collectors.toSet());
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
        return !(o == null || !(o instanceof Group)) && name.equalsIgnoreCase(((Group) o).getName());
    }

    @Override
    public String toString() {
        return "Group{name=" + name + "}";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

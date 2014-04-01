package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A class representing a permissions group.
 */
public class Group {

    private PermissionsPlugin plugin;
    private String name;

    protected Group(final PermissionsPlugin plugin, final String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<String> getPlayers() {
        List<String> result = new ArrayList<>();
        if (plugin.getNode("users") != null) {

            for (String user : plugin.getNode("users").getKeys(false)) {
                for (String group : plugin.getNode("users/" + user).getStringList("groups")) {
                    if (name.equalsIgnoreCase(group) && !result.contains(user)) {
                        result.add(user);
                    }
                }
            }
        }
        return result;
    }

    public List<Player> getOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (String user : getPlayers()) {
            Player player = Bukkit.getServer().getPlayerExact(user);
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    public PermissionInfo getInfo() {
        ConfigurationSection node = plugin.getNode("groups/" + name);
        if (node == null) {
            return null;
        }
        return new PermissionInfo(plugin, node, "inheritance");
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

package ru.gtncraft.permissions;

import com.google.common.collect.ImmutableSet;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class representing the global and world nodes attached to a player or group.
 */
public class PermissionInfo {
    
    private final PermissionManager manager;
    private final ConfigurationSection node;
    private final String groupType;
    
    protected PermissionInfo(final PermissionManager manager, final ConfigurationSection node, final String groupType) {
        this.manager = manager;
        this.node = node;
        this.groupType = groupType;
    }
    
    /**
     * Gets the list of groups this group/player inherits permissions from.
     * @return The list of groups.
     */
    public List<Group> getGroups() {
        return node.getStringList(groupType).stream().map(manager::getGroup).filter(v -> v != null).collect(Collectors.toList());
    }
    
    /**
     * Gets a map of non-world-specific permission nodes to boolean values that this group/player defines.
     * @return The map of permissions.
     */
    public Map<String, Boolean> getPermissions() {
        return manager.getAllPerms(node.getName(), node.getName());
    }
    
    /**
     * Gets a list of worlds this group/player defines world-specific permissions for.
     * @return The list of worlds.
     */
    public Set<String> getWorlds() {
        if (node.getConfigurationSection("worlds") == null) {
            return ImmutableSet.of();
        }
        return node.getConfigurationSection("worlds").getKeys(false);
    }
    
    /**
     * Gets a map of world-specific permission nodes to boolean values that this group/player defines.
     * @param world The name of the world.
     * @return The map of permissions.
     */
    public Map<String, Boolean> getWorldPermissions(final String world) {
        return manager.getAllPerms(node.getName() + ":" + world, node.getName() + "/world/" + world);
    }
}

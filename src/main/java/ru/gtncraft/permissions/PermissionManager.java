package ru.gtncraft.permissions;

import com.google.common.collect.ImmutableSet;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class PermissionManager {

    private final Permissions plugin;
    private final Map<String, PermissionAttachment> permissions;

    public PermissionManager(final Permissions plugin) {
        this.plugin = plugin;
        this.permissions = new HashMap<>();
    }

    // -- External API
    /**
     * Get the group with the given name.
     * @param groupName The name of the group.
     * @return A Group if it exists or null otherwise.
     */
    public Group getGroup(final String groupName) {
        if (getNode("groups") != null) {
            for (String key : getNode("groups").getKeys(false)) {
                if (key.equalsIgnoreCase(groupName)) {
                    return new Group(plugin.getManager(), key);
                }
            }
        }
        return null;
    }

    /**
     * Returns a list of groups a player is in.
     * @param playerName The name of the player.
     * @return The groups this player is in. May be empty.
     */
    public Set<Group> getGroups(final String playerName) {
        ConfigurationSection node = getNode("users/" + playerName);
        if (node == null) {
            return ImmutableSet.of(new Group(plugin.getManager(), "default"));
        }
        return node.getStringList("groups")
                   .stream()
                   .map(k -> new Group(plugin.getManager(), k))
                   .collect(Collectors.toSet());
    }

    /**
     * Returns permission info on the given player.
     * @param playerName The name of the player.
     * @return A PermissionsInfo about this player.
     */
    public PermissionInfo getPlayerInfo(final String playerName) {
        if (getNode("users/" + playerName) == null) {
            return null;
        } else {
            return new PermissionInfo(plugin.getManager(), getNode("users/" + playerName), "groups");
        }
    }

    /**
     * Returns a list of all defined groups.
     * @return The list of groups.
     */
    public Set<Group> getAllGroups() {
        ConfigurationSection node = getNode("groups");
        if (node == null) {
            return ImmutableSet.of(new Group(plugin.getManager(), "default"));
        }
        return node.getKeys(false)
                   .stream()
                   .map(name -> new Group(plugin.getManager(), name))
                   .collect(Collectors.toSet());
    }

    protected void registerPlayer(final Player player) {
        if (permissions.containsKey(player.getName())) {
            unregisterPlayer(player);
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        permissions.put(player.getName(), attachment);
        calculateAttachment(player);
    }

    protected void unregisterPlayer(final Player player) {
        if (permissions.containsKey(player.getName())) {
            try {
                player.removeAttachment(permissions.get(player.getName()));
            } catch (IllegalArgumentException ex) {
            }
            permissions.remove(player.getName());
        }
    }

    protected void refreshForPlayer(String player) {
        plugin.saveConfig();

        Player onlinePlayer = Bukkit.getServer().getPlayerExact(player);
        if (onlinePlayer != null) {
            calculateAttachment(onlinePlayer);
        }
    }

    private void fillChildGroups(Set<String> childGroups, String group) {
        if (childGroups.contains(group)) return;
        childGroups.add(group);

        for (String key : getNode("groups").getKeys(false)) {
            for (String parent : getNode("groups/" + key).getStringList("inheritance")) {
                if (parent.equalsIgnoreCase(group)) {
                    fillChildGroups(childGroups, key);
                }
            }
        }
    }

    protected void refreshForGroup(String group) {
        plugin.saveConfig();

        // build the set of groups which are children of "group"
        // e.g. if Bob is only a member of "expert" which inherits "user", he
        // must be updated if the permissions of "user" change
        HashSet<String> childGroups = new HashSet<>();
        fillChildGroups(childGroups, group);

        for (String player : permissions.keySet()) {
            ConfigurationSection node = getNode("users/" + player);

            // if the player isn't in the config, act like they're in default
            List<String> groupList = (node != null) ? node.getStringList("groups") : Arrays.asList("default");
            for (String userGroup : groupList) {
                if (childGroups.contains(userGroup)) {
                    calculateAttachment(Bukkit.getServer().getPlayerExact(player));
                    break;
                }
            }
        }
    }

    protected void refreshPermissions() {
        permissions.keySet().stream().map(p -> Bukkit.getServer().getPlayerExact(p)).forEach(this::calculateAttachment);
    }

    protected ConfigurationSection getNode(String node) {
        for (String entry : plugin.getConfig().getKeys(true)) {
            if (node.equalsIgnoreCase(entry) && plugin.getConfig().isConfigurationSection(entry)) {
                return plugin.getConfig().getConfigurationSection(entry);
            }
        }
        return null;
    }

    protected void createNode(final String node) {
        ConfigurationSection sec = plugin.getConfig();
        for (String piece : node.split("/")) {
            ConfigurationSection sec2 = getNode(sec == plugin.getConfig() ? piece : sec.getCurrentPath() + "/" + piece);
            if (sec2 == null) {
                sec2 = sec.createSection(piece);
            }
            sec = sec2;
        }
    }

    protected Map<String, Boolean> getAllPerms(String desc, String path) {
        ConfigurationSection node = getNode(path);

        int failures = 0;
        String firstFailure = "";

        // Make an attempt to autofix incorrect nesting
        boolean fixed = false, fixedNow = true;
        while (fixedNow) {
            fixedNow = false;
            for (String key : node.getKeys(true)) {
                if (node.isBoolean(key) && key.contains("/")) {
                    node.set(key.replace("/", "."), node.getBoolean(key));
                    node.set(key, null);
                    fixed = fixedNow = true;
                } else if (node.isConfigurationSection(key) && node.getConfigurationSection(key).getKeys(true).size() == 0) {
                    node.set(key, null);
                    fixed = fixedNow = true;
                }
            }
        }
        if (fixed) {
            plugin.getLogger().info("Fixed broken nesting in " + desc + ".");
            plugin.saveConfig();
        }

        Map<String, Boolean> result = new LinkedHashMap<>();
        // Do the actual getting of permissions
        for (String key : node.getKeys(false)) {
            if (node.isBoolean(key)) {
                result.put(key, node.getBoolean(key));
            } else {
                ++failures;
                if (firstFailure.length() == 0) {
                    firstFailure = key;
                }
            }
        }

        if (failures == 1) {
            plugin.getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean.");
        } else if (failures > 1) {
            plugin.getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean (+" + (failures-1) + " more).");
        }

        return result;
    }


    protected void calculateAttachment(Player player) {
        if (player == null) {
            return;
        }
        PermissionAttachment attachment = permissions.get(player.getName());
        if (attachment == null) {
            return;
        }

        Map<String, Boolean> values = calculatePlayerPermissions(player.getName().toLowerCase(), player.getWorld().getName());

        // Fill the attachment reflectively so we don't recalculate for each permission
        // it turns out there's a lot of permissions!
        Map<String, Boolean> dest = reflectMap(attachment);
        dest.clear();
        dest.putAll(values);

        player.recalculatePermissions();
    }

    // -- Private stuff

    private Field pField;

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> reflectMap(PermissionAttachment attachment) {
        try {
            if (pField == null) {
                pField = PermissionAttachment.class.getDeclaredField("permissions");
                pField.setAccessible(true);
            }
            return (Map<String, Boolean>) pField.get(attachment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // normally, LinkedHashMap.put (and thus putAll) will not reorder the list
    // if that key is already in the map, which we don't want - later puts should
    // always be bumped to the end of the list
    private <K, V> void put(Map<K, V> dest, K key, V value) {
        dest.remove(key);
        dest.put(key, value);
    }

    private <K, V> void putAll(Map<K, V> dest, Map<K, V> src) {
        for (Map.Entry<K, V> entry : src.entrySet()) {
            put(dest, entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Boolean> calculatePlayerPermissions(String player, String world) {
        String playerNode = "users/" + player;

        // if the player isn't in the config, act like they're in default
        if (getNode(playerNode) == null) {
            return calculateGroupPermissions("default", world);
        }

        Map<String, Boolean> perms = new LinkedHashMap<>();

        // first, apply the player's groups (getStringList returns an empty list if not found)
        // later groups override earlier groups
        for (String group : getNode(playerNode).getStringList("groups")) {
            putAll(perms, calculateGroupPermissions(group, world));
        }

        // now apply user-specific permissions
        if (getNode(playerNode + "/permissions") != null) {
            putAll(perms, getAllPerms("user " + player, playerNode + "/permissions"));
        }

        // now apply world- and user-specific permissions
        if (getNode(playerNode + "/worlds/" + world) != null) {
            putAll(perms, getAllPerms("user " + player + " world " + world, playerNode + "/worlds/" + world));
        }

        return perms;
    }

    private Map<String, Boolean> calculateGroupPermissions(String group, String world) {
        return calculateGroupPermissions0(new HashSet<>(), group, world);
    }

    private Map<String, Boolean> calculateGroupPermissions0(Set<String> recursionBuffer, String group, String world) {
        String groupNode = "groups/" + group;

        // if the group's not in the config, nothing
        if (getNode(groupNode) == null) {
            return new LinkedHashMap<>();
        }

        recursionBuffer.add(group);
        Map<String, Boolean> perms = new LinkedHashMap<>();

        // first apply any parent groups (see calculatePlayerPermissions for more)
        for (String parent : getNode(groupNode).getStringList("inheritance")) {
            if (recursionBuffer.contains(parent)) {
                plugin.getLogger().warning("In group " + group + ": recursive inheritance from " + parent);
                continue;
            }

            putAll(perms, calculateGroupPermissions0(recursionBuffer, parent, world));
        }

        // now apply the group's permissions
        if (getNode(groupNode + "/permissions") != null) {
            putAll(perms, getAllPerms("group " + group, groupNode + "/permissions"));
        }

        // now apply world-specific permissions
        if (getNode(groupNode + "/worlds/" + world) != null) {
            putAll(perms, getAllPerms("group " + group + " world " + world, groupNode + "/worlds/" + world));
        }

        return perms;
    }
}

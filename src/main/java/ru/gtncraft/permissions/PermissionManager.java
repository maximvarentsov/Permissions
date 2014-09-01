package ru.gtncraft.permissions;

import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

final public class PermissionManager {

    private final Permissions plugin;
    private final Map<UUID, PermissionAttachment> permissions = new HashMap<>();

    public PermissionManager(final Permissions plugin) {
        this.plugin = plugin;
    }

    // -- External API
    /**
     * Get the group with the given name.
     *
     * @param groupName The name of the group.
     * @return A Group if it exists or null otherwise.
     */
    public Group getGroup(String groupName) {
        ConfigurationSection groups = getNode("groups");
        if (groups != null) {
            for (String key : groups.getKeys(false)) {
                if (key.equalsIgnoreCase(groupName)) {
                    return new Group(this, key);
                }
            }
        }
        return null;
    }

    /**
     * Returns a list of groups a player is in.
     *
     * @param player The name of the player.
     * @return The groups this player is in. May be empty.
     * @deprecated Use UUIDs instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public List<Group> getGroups(String player) {
        List<Group> result = new ArrayList<>();
        ConfigurationSection node = getUsernameNode(player);
        if (node == null) {
            result.add(new Group(this, "default"));
        } else {
            for (String key : node.getStringList("groups")) {
                result.add(new Group(this, key));
            }
        }
        return result;
    }

    /**
     * Returns permission info on the given player.
     *
     * @param player The name of the player.
     * @return A PermissionsInfo about this player.
     */
    @SuppressWarnings("unused")
    public PermissionInfo getPlayerInfo(String player) {
        ConfigurationSection node = getNode("users/" + player);
        if (node != null) {
            return new PermissionInfo(plugin.getManager(), node, "groups");
        }
        return null;
    }
    /**
     * Returns a list of all defined groups.
     * @return The list of groups.
     */
    @SuppressWarnings("unused")
    public Collection<Group> getAllGroups() {
        ConfigurationSection node = getNode("groups");
        if (node == null) {
            return ImmutableList.of(new Group(plugin.getManager(), "default"));
        }
        return node.getKeys(false)
                   .stream()
                   .map(name -> new Group(plugin.getManager(), name))
                   .collect(Collectors.toList());
    }

    public void registerPlayer(final Player player) {
        if (permissions.containsKey(player.getUniqueId())) {
            unregisterPlayer(player);
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        permissions.put(player.getUniqueId(), attachment);
        calculateAttachment(player);
    }

    public void unregisterPlayer(final Player player) {
        if (permissions.containsKey(player.getUniqueId())) {
            try {
                player.removeAttachment(permissions.get(player.getUniqueId()));
            } catch (IllegalArgumentException ignore) {
            }
            permissions.remove(player.getUniqueId());
        }
    }

    public void refreshForPlayer(final UUID uuid) {
        plugin.saveConfig();
        Player onlinePlayer = Bukkit.getServer().getPlayer(uuid);
        if (onlinePlayer != null) {
            calculateAttachment(onlinePlayer);
        }
    }

    void fillChildGroups(Set<String> childGroups, String group) {
        if (childGroups.contains(group)) {
            return;
        }
        childGroups.add(group);

        for (String key : getNode("groups").getKeys(false)) {
            for (String parent : getNode("groups/" + key).getStringList("inheritance")) {
                if (parent.equalsIgnoreCase(group)) {
                    fillChildGroups(childGroups, key);
                }
            }
        }
    }

    public void refreshForGroup(String group) {
        plugin.saveConfig();

        // build the set of groups which are children of "group"
        // e.g. if Bob is only a member of "expert" which inherits "user", he
        // must be updated if the permissions of "user" change
        Set<String> childGroups = new HashSet<>();
        fillChildGroups(childGroups, group);

        for (UUID uuid : permissions.keySet()) {
            Player player = Bukkit.getServer().getPlayer(uuid);
            ConfigurationSection node = getUserNode(player);

            // if the player isn't in the config, act like they're in default
            List<String> groupList = (node != null) ? node.getStringList("groups") : Arrays.asList("default");
            for (String userGroup : groupList) {
                if (childGroups.contains(userGroup)) {
                    calculateAttachment(player);
                    break;
                }
            }
        }
    }

    public void refreshPermissions() {
        for (UUID player : permissions.keySet()) {
            calculateAttachment(Bukkit.getServer().getPlayer(player));
        }
    }

    protected ConfigurationSection getNode(String node) {
        for (String entry : plugin.getConfig().getKeys(true)) {
            if (node.equalsIgnoreCase(entry) && plugin.getConfig().isConfigurationSection(entry)) {
                return plugin.getConfig().getConfigurationSection(entry);
            }
        }
        return null;
    }

    protected ConfigurationSection getUserNode(Player player) {
        ConfigurationSection sec = getNode("users/" + player.getUniqueId());
        if (sec == null) {
            sec = getNode("users/" + player.getName());
            if (sec != null) {
                plugin.getConfig().set(sec.getCurrentPath(), null);
                plugin.getConfig().set("users/" + player.getUniqueId(), sec);
                sec.set("name", player.getName());
                plugin.saveConfig();
            }
        }

        // make sure name field matches
        if (sec != null) {
            if (!player.getName().equals(sec.getString("name"))) {
                sec.set("name", player.getName());
                plugin.saveConfig();
            }
        }

        return sec;
    }

    protected ConfigurationSection getUsernameNode(String name) {
        // try to look up node based on username rather than UUID
        ConfigurationSection sec = getNode("users");
        if (sec != null) {
            for (String child : sec.getKeys(false)) {
                ConfigurationSection node = sec.getConfigurationSection(child);
                if (node != null && (name.equals(node.getString("name")) || name.equals("child"))) {
                    // either the "name" field matches or the key matches
                    return node;
                }
            }
        }
        return null;
    }

    public ConfigurationSection createNode(final String node) {
        ConfigurationSection sec = plugin.getConfig();
        for (String piece : node.split("/")) {
            ConfigurationSection sec2 = getNode(sec == plugin.getConfig() ? piece : sec.getCurrentPath() + "/" + piece);
            if (sec2 == null) {
                sec2 = sec.createSection(piece);
            }
            sec = sec2;
        }
        return sec;
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

    protected void calculateAttachment(final Player player) {
        if (player == null) {
            return;
        }
        PermissionAttachment attachment = permissions.get(player.getUniqueId());
        if (attachment == null) {
            return;
        }

        Map<String, Boolean> values = calculatePlayerPermissions(player, player.getWorld().getName());

        // Fill the attachment reflectively so we don't recalculate for each permission
        // it turns out there's a lot of permissions!
        Map<String, Boolean> dest = reflectMap(attachment);
        dest.clear();
        dest.putAll(values);

        player.recalculatePermissions();
    }

    Field pField;

    @SuppressWarnings("unchecked")
    Map<String, Boolean> reflectMap(PermissionAttachment attachment) {
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
    <K, V> void put(Map<K, V> dest, K key, V value) {
        dest.remove(key);
        dest.put(key, value);
    }

    <K, V> void putAll(Map<K, V> dest, Map<K, V> src) {
        for (Map.Entry<K, V> entry : src.entrySet()) {
            put(dest, entry.getKey(), entry.getValue());
        }
    }

    Map<String, Boolean> calculatePlayerPermissions(Player player, String world) {
        ConfigurationSection node = getUserNode(player);

        // if the player isn't in the config, act like they're in default
        if (node == null) {
            return calculateGroupPermissions("default", world);
        }

        String nodePath = node.getCurrentPath();
        Map<String, Boolean> perms = new LinkedHashMap<>();

        // first, apply the player's groups (getStringList returns an empty list if not found)
        // later groups override earlier groups
        for (String group : node.getStringList("groups")) {
            putAll(perms, calculateGroupPermissions(group, world));
        }

        // now apply user-specific permissions
        if (getNode(nodePath + "/permissions") != null) {
            putAll(perms, getAllPerms("user " + player, nodePath + "/permissions"));
        }

        // now apply world- and user-specific permissions
        if (getNode(nodePath + "/worlds/" + world) != null) {
            putAll(perms, getAllPerms("user " + player + " world " + world, nodePath + "/worlds/" + world));
        }

        return perms;
    }

    Map<String, Boolean> calculateGroupPermissions(String group, String world) {
        return calculateGroupPermissions0(new HashSet<>(), group, world);
    }

    Map<String, Boolean> calculateGroupPermissions0(Set<String> recursionBuffer, String group, String world) {
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

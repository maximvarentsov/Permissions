package ru.gtncraft.permissions;

import com.google.common.collect.ImmutableList;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

final class Commands implements CommandExecutor, TabCompleter {
    
    final Permissions plugin;

    public Commands(final Permissions plugin) {
        plugin.getCommand("permissions").setExecutor(this);
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        if (split.length < 1) {
            return !checkPerm(sender, "help") || usage(sender, command);
        }
        
        String subCommand = split[0];
        switch (subCommand) {
            case "reload":
                if (!checkPerm(sender, "reload")) {
                    return true;
                }
                plugin.reloadConfig();
                if (plugin.configLoadError) {
                    plugin.configLoadError = false;
                    sender.sendMessage(ChatColor.RED + "Your configuration is invalid, see the console for details.");
                } else {
                    plugin.getManager().refreshPermissions();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                }
                return true;
            case "check": {
                if (!checkPerm(sender, "check")) {
                    return true;
                }
                if (split.length != 2 && split.length != 3) {
                    return usage(sender, command, subCommand);
                }

                String node = split[1];
                Permissible permissible;
                if (split.length == 2) {
                    permissible = sender;
                } else {
                    permissible = plugin.getServer().getPlayer(split[2]);
                }

                String name = (permissible instanceof Player) ? ((Player) permissible).getName() : (permissible instanceof ConsoleCommandSender) ? "Console" : "Unknown";

                if (permissible == null) {
                    sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + split[2] + ChatColor.RED + " not found.");
                } else {
                    boolean set = permissible.isPermissionSet(node), has = permissible.hasPermission(node);
                    String sets = set ? " sets " : " defaults ";
                    String perm = has ? "true" : "false";
                    sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + name + ChatColor.GREEN + sets + ChatColor.WHITE + node + ChatColor.GREEN + " to " + ChatColor.WHITE + perm + ChatColor.GREEN + ".");
                }
                return true;
            }
            case "info": {
                if (!checkPerm(sender, "info")) {
                    return true;
                }
                if (split.length != 2) {
                    return usage(sender, command, subCommand);
                }

                String node = split[1];
                Permission perm = plugin.getServer().getPluginManager().getPermission(node);

                if (perm == null) {
                    sender.sendMessage(ChatColor.RED + "Permission " + ChatColor.WHITE + node + ChatColor.RED + " not found.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Info on permission " + ChatColor.WHITE + perm.getName() + ChatColor.GREEN + ":");
                    sender.sendMessage(ChatColor.GREEN + "Default: " + ChatColor.WHITE + perm.getDefault());
                    if (perm.getDescription() != null && perm.getDescription().length() > 0) {
                        sender.sendMessage(ChatColor.GREEN + "Description: " + ChatColor.WHITE + perm.getDescription());
                    }
                    if (perm.getChildren() != null && perm.getChildren().size() > 0) {
                        sender.sendMessage(ChatColor.GREEN + "Children: " + ChatColor.WHITE + perm.getChildren().size());
                    }
                    if (perm.getPermissibles() != null && perm.getPermissibles().size() > 0) {
                        int num = 0, numTrue = 0;
                        for (Permissible who : perm.getPermissibles()) {
                            ++num;
                            if (who.hasPermission(perm)) {
                                ++numTrue;
                            }
                        }
                        sender.sendMessage(ChatColor.GREEN + "Set on: " + ChatColor.WHITE + num + ChatColor.GREEN + " (" + ChatColor.WHITE + numTrue + ChatColor.GREEN + " true)");
                    }
                }
                return true;
            }
            case "dump": {
                if (!checkPerm(sender, "dump")) {
                    return true;
                }
                if (split.length < 1 || split.length > 3) {
                    return usage(sender, command, subCommand);
                }

                int page;
                Permissible permissible;
                if (split.length == 1) {
                    permissible = sender;
                    page = 1;
                } else if (split.length == 2) {
                    permissible = sender;
                    try {
                        page = Integer.parseInt(split[1]);
                    } catch (NumberFormatException ex) {
                        if (split[1].equalsIgnoreCase("-file")) {
                            page = -1;
                        } else {
                            permissible = plugin.getServer().getPlayer(split[1]);
                            page = 1;
                        }
                    }
                } else {
                    permissible = plugin.getServer().getPlayer(split[1]);
                    try {
                        page = Integer.parseInt(split[2]);
                    } catch (NumberFormatException ex) {
                        if (split[2].equalsIgnoreCase("-file")) {
                            page = -1;
                        } else {
                            page = 1;
                        }
                    }
                }

                if (permissible == null) {
                    sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + split[1] + ChatColor.RED + " not found.");
                    return true;
                }

                List<PermissionAttachmentInfo> dump = new ArrayList<>(permissible.getEffectivePermissions());
                Collections.sort(dump, (a, b) -> a.getPermission().compareTo(b.getPermission()));

                if (page == -1) {
                    // Dump to file
                    File file = new File(plugin.getDataFolder(), "dump.txt");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        try (PrintStream out = new PrintStream(fos)) {
                            // right now permissible is always a CommandSender
                            out.println("Permissions dump for: " + ((CommandSender) permissible).getName());
                            out.println(new Date().toString());
                            for (PermissionAttachmentInfo info : dump) {
                                if (info.getAttachment() == null) {
                                    out.println(info.getPermission() + "=" + info.getValue() + " (default)");
                                } else {
                                    out.println(info.getPermission() + "=" + info.getValue() + " (" + info.getAttachment().getPlugin().getDescription().getName() + ")");
                                }
                            }
                        }
                        sender.sendMessage(ChatColor.GREEN + "Permissions dump written to " + ChatColor.WHITE + file);
                    } catch (IOException ex) {
                        sender.sendMessage(ChatColor.RED + "Failed to write to dump.txt, see the console for more details");
                        sender.sendMessage(ChatColor.RED + ex.toString());
                        ex.printStackTrace();
                    }
                    return true;
                }

                int numpages = 1 + (dump.size() - 1) / 8;
                if (page > numpages) {
                    page = numpages;
                } else if (page < 1) {
                    page = 1;
                }

                ChatColor g = ChatColor.GREEN, w = ChatColor.WHITE, r = ChatColor.RED;

                int start = 8 * (page - 1);
                sender.sendMessage(ChatColor.RED + "[==== " + ChatColor.GREEN + "Page " + page + " of " + numpages + ChatColor.RED + " ====]");
                for (int i = start; i < start + 8 && i < dump.size(); ++i) {
                    PermissionAttachmentInfo info = dump.get(i);

                    if (info.getAttachment() == null) {
                        sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g + " (" + r + "default" + g + ")");
                    } else {
                        sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g + " (" + w + info.getAttachment().getPlugin().getDescription().getName() + g + ")");
                    }
                }
                return true;
            }
            case "rank":
            case "setrank":
                if (!checkPerm(sender, "setrank")) return true;
                if (split.length != 3) return usage(sender, command, subCommand);

                // This is essentially player setgroup with an added check
                UUID player = resolvePlayer(sender, split[1]);
                if (player == null) {
                    return true;
                }
                String group = split[2];

                if (!sender.hasPermission("permissions.setrank." + group)) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to add players to " + ChatColor.WHITE + group + ChatColor.RED + ".");
                    return true;
                }

                plugin.getManager().createNode("users/" + player).set("groups", Arrays.asList(group));
                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in " + ChatColor.WHITE + group + ChatColor.GREEN + ".");
                return true;
            case "group":
                if (split.length < 2) {
                    return !checkPerm(sender, "group.help") || usage(sender, command, subCommand);
                }
                groupCommand(sender, command, split);
                return true;
            case "player":
                if (split.length < 2) {
                    return !checkPerm(sender, "player.help") || usage(sender, command, subCommand);
                }
                playerCommand(sender, command, split);
                return true;
            default:
                return !checkPerm(sender, "help") || usage(sender, command);
        }
    }

    boolean groupCommand(CommandSender sender, Command command, String[] split) {
        String subcommand = split[1];

        switch (subcommand) {
            case "list": {
                if (!checkPerm(sender, "group.list")) {
                    return true;
                }
                if (split.length != 2) {
                    return usage(sender, command, "group list");
                }

                String result = "", sep = "";
                for (String key : plugin.getManager().getNode("groups").getKeys(false)) {
                    result += sep + key;
                    sep = ", ";
                }
                sender.sendMessage(ChatColor.GREEN + "Groups: " + ChatColor.WHITE + result);
                return true;
            }
            case "players": {
                if (!checkPerm(sender, "group.players")) {
                    return true;
                }
                if (split.length != 3) {
                    return usage(sender, command, "group players");
                }
                String group = split[2];

                if (plugin.getManager().getNode("groups/" + group) == null) {
                    sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
                    return true;
                }

                int count = 0;
                String text = "", sep = "";
                for (String user : plugin.getManager().getNode("users").getKeys(false)) {
                    if (plugin.getManager().getNode("users/" + user).getStringList("groups").contains(group)) {
                        ++count;
                        text += sep + user;
                        sep = ", ";
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Users in " + ChatColor.WHITE + group + ChatColor.GREEN + " (" + ChatColor.WHITE + count + ChatColor.GREEN + "): " + ChatColor.WHITE + text);
                return true;
            }
            case "setperm": {
                if (!checkPerm(sender, "group.setperm")) {
                    return true;
                }
                if (split.length != 4 && split.length != 5) {
                    return usage(sender, command, "group setperm");
                }
                String group = split[2];
                String perm = split[3];
                boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

                String node = "permissions";
                if (plugin.getManager().getNode("groups/" + group) == null) {
                    sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
                    return true;
                }

                if (perm.contains(":")) {
                    String world = perm.substring(0, perm.indexOf(':'));
                    perm = perm.substring(perm.indexOf(':') + 1);
                    node = "worlds/" + world;
                }

                plugin.getManager().createNode("groups/" + group + "/" + node).set(perm, value);
                plugin.getManager().refreshForGroup(group);

                sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " now has " + ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
                return true;
            }
            case "unsetperm": {
                if (!checkPerm(sender, "group.unsetperm")) {
                    return true;
                }
                if (split.length != 4) {
                    return usage(sender, command, "group unsetperm");
                }
                String group = split[2].toLowerCase();
                String perm = split[3];

                String node = "permissions";
                if (plugin.getManager().getNode("groups/" + group) == null) {
                    sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
                    return true;
                }

                if (perm.contains(":")) {
                    String world = perm.substring(0, perm.indexOf(':'));
                    perm = perm.substring(perm.indexOf(':') + 1);
                    node = "worlds/" + world;
                }

                ConfigurationSection sec = plugin.getManager().createNode("groups/" + group + "/" + node);
                if (!sec.contains(perm)) {
                    sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " did not have " + ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
                    return true;
                }
                sec.set(perm, null);
                plugin.getManager().refreshForGroup(group);

                sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " no longer has " + ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
                return true;
            }
            default:
                return !checkPerm(sender, "group.help") || usage(sender, command);
        }
    }

    boolean playerCommand(CommandSender sender, Command command, String[] split) {
        String subcommand = split[1];

        switch (subcommand) {
            case "groups": {
                if (!checkPerm(sender, "player.groups")) {
                    return true;
                }
                if (split.length != 3) {
                    return usage(sender, command, "player groups");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }

                if (plugin.getManager().getNode("users/" + player) == null) {
                    sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.RED + " is in the default group.");
                    return true;
                }

                int count = 0;
                String text = "", sep = "";
                for (String group : plugin.getManager().getNode("users/" + player).getStringList("groups")) {
                    ++count;
                    text += sep + group;
                    sep = ", ";
                }
                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is in groups (" + ChatColor.WHITE + count + ChatColor.GREEN + "): " + ChatColor.WHITE + text);
                return true;
            }
            case "setgroup": {
                if (!checkPerm(sender, "player.setgroup")) {
                    return true;
                }
                if (split.length != 4) {
                    return usage(sender, command, "player setgroup");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }
                String[] groups = split[3].split(",");

                plugin.getManager().createNode("users/" + player).set("groups", Arrays.asList(groups));
                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in " + ChatColor.WHITE + split[3] + ChatColor.GREEN + ".");
                return true;
            }
            case "addgroup": {
                if (!checkPerm(sender, "player.addgroup")) {
                    return true;
                }
                if (split.length != 4) {
                    return usage(sender, command, "player addgroup");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }
                String group = split[3];

                List<String> list = plugin.getManager().createNode("users/" + player).getStringList("groups");

                if (list.contains(group)) {
                    sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " was already in " + ChatColor.WHITE + group + ChatColor.GREEN + ".");
                    return true;
                }
                list.add(group);
                plugin.getManager().getNode("users/" + player).set("groups", list);

                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in " + ChatColor.WHITE + group + ChatColor.GREEN + ".");
                return true;
            }
            case "removegroup": {
                if (!checkPerm(sender, "player.removegroup")) {
                    return true;
                }
                if (split.length != 4) {
                    return usage(sender, command, "player removegroup");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }
                String group = split[3];

                List<String> list = plugin.getManager().createNode("users/" + player).getStringList("groups");
                if (!list.contains(group)) {
                    sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " was not in " + ChatColor.WHITE + group + ChatColor.GREEN + ".");
                    return true;
                }
                list.remove(group);
                plugin.getManager().getNode("users/" + player).set("groups", list);

                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is no longer in " + ChatColor.WHITE + group + ChatColor.GREEN + ".");
                return true;
            }
            case "setperm": {
                if (!checkPerm(sender, "player.setperm")) {
                    return true;
                }
                if (split.length != 4 && split.length != 5) {
                    return usage(sender, command, "player setperm");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }
                String perm = split[3];
                boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

                String node = "permissions";

                if (perm.contains(":")) {
                    String world = perm.substring(0, perm.indexOf(':'));
                    perm = perm.substring(perm.indexOf(':') + 1);
                    node = "worlds/" + world;
                }

                plugin.getManager().createNode("users/" + player + "/" + node).set(perm, value);
                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " now has " + ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
                return true;
            }
            case "unsetperm": {
                if (!checkPerm(sender, "player.unsetperm")) {
                    return true;
                }
                if (split.length != 4) {
                    return usage(sender, command, "player unsetperm");
                }
                UUID player = resolvePlayer(sender, split[2]);
                if (player == null) {
                    return true;
                }
                String perm = split[3];

                String node = "permissions";

                if (perm.contains(":")) {
                    String world = perm.substring(0, perm.indexOf(':'));
                    perm = perm.substring(perm.indexOf(':') + 1);
                    node = "worlds/" + world;
                }

                ConfigurationSection sec = plugin.getManager().createNode("users/" + player + "/" + node);

                if (!sec.contains(perm)) {
                    sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " did not have " + ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
                    return true;
                }
                sec.set(perm, null);
                plugin.getManager().refreshForPlayer(player);

                sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " no longer has " + ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
                return true;
            }
            default:
                return !checkPerm(sender, "player.help") || usage(sender, command);
        }
    }

    UUID resolvePlayer(CommandSender sender, String arg) {
        arg = arg.toLowerCase();
        // see if it resolves to a single player name
        List<Player> players = plugin.getServer().matchPlayer(arg);
        if (players.size() == 1) {
            return players.get(0).getUniqueId();
        } else if (players.size() > 1) {
            sender.sendMessage(ChatColor.RED + "Username " + ChatColor.WHITE + arg + ChatColor.RED + " is ambiguous.");
            return null;
        }

        if (arg.length() == 32) {
            // expand UUIDs which do not have dashes in them
            arg = arg.substring(0, 8) + "-" + arg.substring(8, 12) + "-" + arg.substring(12, 16) +
                  "-" + arg.substring(16, 20) + "-" + arg.substring(20, 32);
        }
        if (arg.length() == 36) {
            // is of correct UUID length
            try {
                return UUID.fromString(arg);
            } catch (IllegalArgumentException ignore) {
            }
        }
        sender.sendMessage(ChatColor.RED + "Could not resolve player: " + ChatColor.WHITE + arg);
        sender.sendMessage(ChatColor.RED + "You must provide a UUID or the name of an online player.");
        return null;
    }
    
    // -- utilities --
    
    boolean checkPerm(CommandSender sender, String subnode) {
        boolean ok = sender.hasPermission("permissions." + subnode);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "You do not have permissions to do that.");
        }
        return ok;
    }
    
    boolean usage(CommandSender sender, Command command) {
        sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + ChatColor.RED + "====]");
        for (String line : command.getUsage().split("\\n")) {
            if ((line.startsWith("/<command> group") && !line.startsWith("/<command> group -")) ||
                (line.startsWith("/<command> player") && !line.startsWith("/<command> player -"))) {
                continue;
            }
            sender.sendMessage(formatLine(line));
        }
        return true;
    }
    
    boolean usage(CommandSender sender, Command command, String subcommand) {
        sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + subcommand + " " + ChatColor.RED + "====]");
        for (String line : command.getUsage().split("\\n")) {
            if (line.startsWith("/<command> " + subcommand)) {
                sender.sendMessage(formatLine(line));
            }
        }
        return true;
    }
    
     String formatLine(String line) {
        int i = line.indexOf(" - ");
        String usage = line.substring(0, i);
        String desc = line.substring(i + 3);

        usage = usage.replace("<command>", "permissions");
        usage = usage.replaceAll("\\[[^]:]+\\]", ChatColor.AQUA + "$0" + ChatColor.GREEN);
        usage = usage.replaceAll("\\[[^]]+:\\]", ChatColor.AQUA + "$0" + ChatColor.LIGHT_PURPLE);
        usage = usage.replaceAll("<[^>]+>", ChatColor.LIGHT_PURPLE + "$0" + ChatColor.GREEN);

        return ChatColor.GREEN + usage + " - " + ChatColor.WHITE + desc;
    }

    final List<String> BOOLEAN = ImmutableList.of("true", "false");
    final List<String> ROOT_SUBS = ImmutableList.of("reload", "about", "check", "info", "dump", "rank", "setrank", "group", "player");
    final List<String> GROUP_SUBS = ImmutableList.of("list", "players", "setperm", "unsetperm");
    final List<String> PLAYER_SUBS = ImmutableList.of("setgroup", "addgroup", "removegroup", "setperm", "unsetperm");

    final Set<Permission> permSet = new HashSet<>();
    final List<String> permList = new ArrayList<>();

    List<String> groupComplete(CommandSender sender, String[] args) {
        String sub = args[1];
        String lastArg = args[args.length - 1];
        /*
        group list - list all groups.
        group players <group> - list players in a group.
        group setperm <group> <[world:]node> [true|false] - set a permission on a group.
        group unsetperm <group> <[world:]node> - unset a permission on a group.
         */

        switch (sub) {
            case "players":
                if (args.length == 3) {
                    return partial(lastArg, allGroups());
                }
                break;
            case "setperm":
                if (args.length == 3) {
                    return partial(lastArg, allGroups());
                } else if (args.length == 4) {
                    return worldNodeComplete(lastArg);
                } else if (args.length == 5) {
                    return partial(lastArg, BOOLEAN);
                }
                break;
            case "unsetperm":
                if (args.length == 3) {
                    return partial(lastArg, allGroups());
                } else if (args.length == 4) {
                    // TODO: maybe only show nodes that are already set?
                    return worldNodeComplete(lastArg);
                }
                break;
        }

        return ImmutableList.of();
    }

    List<String> playerComplete(CommandSender sender, String[] args) {
        String sub = args[1];
        String lastArg = args[args.length - 1];
        /*
        player groups <player> - list groups a player is in.
        player setgroup <player> <group,...> - set a player to be in only the given groups.
        player addgroup <player> <group> - add a player to a group.
        player removegroup <player> <group> - remove a player from a group.
        player setperm <player> <[world:]node> [true|false] - set a permission on a player.
        player unsetperm <player> <[world:]node> - unset a permission on a player.
         */

        switch (sub) {
            case "groups":
                if (args.length == 3) {
                    return null;
                }
                break;
            case "setgroup":
                if (args.length == 3) {
                    return null;
                } else if (args.length == 4) {
                    // do some magic to complete after any commas
                    int idx = lastArg.lastIndexOf(',');
                    if (idx == -1) {
                        return partial(lastArg, allGroups());
                    } else {
                        String done = lastArg.substring(0, idx + 1); // includes the comma
                        String toComplete = lastArg.substring(idx + 1);
                        List<String> groups = partial(toComplete, allGroups());
                        return groups.stream().map(group -> done + group).collect(Collectors.toList());
                    }
                }
                break;
            case "addgroup":
            case "removegroup":
                if (args.length == 3) {
                    return null;
                } else if (args.length == 4) {
                    return partial(lastArg, allGroups());
                }
                break;
            case "setperm":
                if (args.length == 3) {
                    return null;
                } else if (args.length == 4) {
                    return worldNodeComplete(lastArg);
                } else if (args.length == 5) {
                    return partial(lastArg, BOOLEAN);
                }
                break;
            case "unsetperm":
                if (args.length == 3) {
                    return null;
                } else if (args.length == 4) {
                    // TODO: maybe only show nodes that are already set?
                    return worldNodeComplete(lastArg);
                }
                break;
        }

        return ImmutableList.of();
    }

    Collection<String> allGroups() {
        return plugin.getConfig().getConfigurationSection("groups").getKeys(false);
    }

    Collection<String> allNodes() {
        Set<Permission> newPermSet = plugin.getServer().getPluginManager().getPermissions();
        if (!permSet.equals(newPermSet)) {
            permSet.clear();
            permSet.addAll(newPermSet);

            permList.clear();
            permList.addAll(permSet.stream().map(Permission::getName).collect(Collectors.toList()));
            Collections.sort(permList);
        }
        return permList;
    }

    List<String> worldNodeComplete(String token) {
        // TODO: complete [world:]node
        return partial(token, allNodes());
    }

    List<String> partial(String token, Collection<String> from) {
        return StringUtil.copyPartialMatches(token, from, new ArrayList<>(from.size()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        // Remember that we can return null to default to online player name matching

        /*
        reload - reload the configuration from disk.
        check <node> [player] - check if a player or the sender has a permission (any plugin).
        info <node> - prints information on a specific permission.
        dump [player] [page] - prints info about a player's (or the sender's) permissions.
        setrank <player> <group> - set a player to be in a group with per-group permissions.
        group - list group-related commands.
        player - list player-related commands.
         */
        String lastArg = args[args.length - 1];

        if (args.length <= 1) {
            return partial(args[0], ROOT_SUBS);
        } else if (args.length == 2) {
            String sub = args[0];
            switch (sub) {
                case "check":
                    return partial(lastArg, allNodes());
                case "info":
                    return partial(lastArg, allNodes());
                case "dump":
                    return null;
                case "rank":
                case "setrank":
                    return null;
                case "group":
                    return partial(lastArg, GROUP_SUBS);
                case "player":
                    return partial(lastArg, PLAYER_SUBS);
            }
        } else {
            String sub = args[0];
            // note that dump is excluded here because there's no real reason to tab-complete page numbers
            if (sub.equals("check") && args.length == 3) {
                return null;
            } else if ((sub.equals("rank") || sub.equals("setrank")) && args.length == 3) {
                return partial(lastArg, allGroups());
            } else if (sub.equals("group")) {
                return groupComplete(sender, args);
            } else if (sub.equals("player")) {
                return playerComplete(sender, args);
            }
        }

        return ImmutableList.of();
    }
}

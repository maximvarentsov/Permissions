package ru.gtncraft.permissions.commands;

import com.google.common.collect.ImmutableList;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import ru.gtncraft.permissions.Permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final public class Reload implements CommandExecutor, TabCompleter {
    
    private final Permissions plugin;
    private final List<String> ROOT_SUBS = ImmutableList.of("reload");

    public Reload(final Permissions plugin) {
        plugin.getCommand("permissions").setExecutor(this);
        plugin.getCommand("permissions").setPermission("permissions.reload");
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        if (split.length < 1) {
            return false;
        }
        switch (split[0]) {
            case "reload":
                plugin.reloadConfig();
                if (plugin.configLoadError) {
                    plugin.configLoadError = false;
                    sender.sendMessage(ChatColor.RED + "Your configuration is invalid, see the console for details.");
                } else {
                    plugin.getManager().refreshPermissions();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                }
                return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        if (args.length <= 1) {
            return partial(args[0], ROOT_SUBS);
        }
        return ImmutableList.of();
    }

    List<String> partial(String token, Collection<String> from) {
        return StringUtil.copyPartialMatches(token, from, new ArrayList<>(from.size()));
    }
}

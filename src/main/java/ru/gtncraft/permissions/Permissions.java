package ru.gtncraft.permissions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final public class Permissions extends JavaPlugin {

    YamlConfiguration config;
    PermissionManager manager;
    boolean configLoadError = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        reloadConfig();

        new Listeners(this);
        new Commands(this);

        manager = new PermissionManager(this);
        Bukkit.getOnlinePlayers().forEach(getManager()::registerPlayer);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(getManager()::unregisterPlayer);
    }

    @Override
    public FileConfiguration getConfig() {
        return config;
    }

    @Override
    public void reloadConfig() {
        config = new YamlConfiguration();
        config.options().pathSeparator('/');
        try {
            config.load(new File(getDataFolder(), "config.yml"));
        } catch (InvalidConfigurationException ex) {
            configLoadError = true;

            // extract line numbers from the exception if we can
            List<String> lines = new ArrayList<>();
            Pattern pattern = Pattern.compile("line (\\d+), column");
            Matcher matcher = pattern.matcher(ex.getMessage());
            while (matcher.find()) {
                String lineNo = matcher.group(1);
                if (!lines.contains(lineNo)) {
                    lines.add(lineNo);
                }
            }

            // make a nice message
            String msg = "Your configuration is invalid! ";
            if (lines.isEmpty()) {
                msg += "Unable to find any line numbers.";
            } else {
                msg += "Take a look at line(s): " + lines.get(0);
                for (String lineNo : lines.subList(1, lines.size())) {
                    msg += ", " + lineNo;
                }
            }
            getLogger().severe(msg);

            // save the whole error to config_error.txt
            File outFile = new File(getDataFolder(), "config_error.txt");
            try (PrintStream out = new PrintStream(new FileOutputStream(outFile))) {
                out.println("Use the following website to help you find and fix configuration errors:");
                out.println("https://yaml-online-parser.appspot.com/");
                out.println();
                out.println(ex.toString());
                getLogger().info("Saved the full error message to " + outFile);
            } catch (IOException ex2) {
                getLogger().severe("Failed to save the full error message!");
            }

            // save a backup
            File backupFile = new File(getDataFolder(), "config_backup.yml");
            File sourceFile = new File(getDataFolder(), "config.yml");
            if (FileUtil.copy(sourceFile, backupFile)) {
                getLogger().info("Saved a backup of your configuration to " + backupFile);
            } else {
                getLogger().severe("Failed to save a configuration backup!");
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", ex);
        }
    }

    @Override
    public void saveConfig() {
        // If there's no keys (such as in the event of a load failure) don't save
        if (config.getKeys(true).isEmpty()) {
            try {
                config.save(new File(getDataFolder(), "config.yml"));
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Failed to save configuration", ex);
            }
        }
    }

    public PermissionManager getManager() {
        return manager;
    }
}

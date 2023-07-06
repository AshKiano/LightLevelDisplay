package com.ashkiano.lightleveldisplay;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//TODO upravit tak, aby si každý hráč mohl vybrat jestli chce to do chatu nebo bossbaru
public class LightLevelDisplay extends JavaPlugin implements CommandExecutor, Listener {

    // Store a map of player UUIDs to boolean values, indicating whether light level display is enabled for each player
    private final Map<UUID, Boolean> lightLevelDisplayMap = new HashMap<>();
    // Declare FileConfiguration and File objects to store the language configuration file
    private FileConfiguration languageConfig = null;
    private File languageConfigFile = null;

    @Override
    public void onEnable() {
        // Register the command executor and event listener when the plugin is enabled
        this.getCommand("lightlevel").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        // Save the default configuration file if it does not already exist
        this.saveDefaultConfig();

        // Create a directory named "languages" in the plugin's data folder
        File languageFolder = new File(getDataFolder(), "languages");
        if (!languageFolder.exists()) {
            languageFolder.mkdirs();
        }

        // Check if language files exist, if not, save them from the resources
        String[] languages = {"en", "cs", "de", "es", "fr", "sk", "pl", "ru", "it", "el", "uk"};
        for (String lang : languages) {
            File langFile = new File(languageFolder, lang + ".yml");
            if (!langFile.exists()) {
                this.saveResource("languages/" + lang + ".yml", false);
            }
        }

        // Reload the language config file
        reloadLanguageConfig();

        Metrics metrics = new Metrics(this, 18811);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the sender is a player. If not, send a message and return false
        if (!(sender instanceof Player)) {
            sender.sendMessage(translateColorCodes(languageConfig.getString("not-a-player-message", "&cThis command can only be used by players.")));
            return false;
        }

        // Cast the sender to Player
        Player player = (Player) sender;

        // Check if the player has the necessary permission to use the command
        boolean checkPermission = getConfig().getBoolean("check-permission", true);
        if (checkPermission && !player.hasPermission(getConfig().getString("permission-node", "lightdisplay"))) {
            // If not, send a message and return false
            player.sendMessage(translateColorCodes(languageConfig.getString("no-permission-message", "&cYou don't have permission to use this command.")));
            return false;
        }

        // Get the unique ID of the player
        UUID playerUUID = player.getUniqueId();

        // Get the current status of light level display for the player, defaulting to false if not present
        boolean displayLightLevel = lightLevelDisplayMap.getOrDefault(playerUUID, false);

        // Toggle the status of light level display for the player
        lightLevelDisplayMap.put(playerUUID, !displayLightLevel);

        // Prepare a message to inform the player whether light level display has been enabled or disabled
        String message = !displayLightLevel ?
                languageConfig.getString("light-level-enabled", "&aLight level display has been enabled.") :
                languageConfig.getString("light-level-disabled", "&cLight level display has been disabled.");

        // Send the message to the player
        player.sendMessage(translateColorCodes(message));

        // Return true because the command has been executed successfully
        return true;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Handle the PlayerMoveEvent

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // If the player has not enabled the light level display, do nothing
        if (!lightLevelDisplayMap.getOrDefault(playerUUID, false)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // If the player has not actually moved to a different block, do nothing
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        // Get the light level of the block the player moved to
        int lightLevel = to.getBlock().getLightLevel();

        // Prepare the message to send to the player, based on the light level
        String monsterSpawnMessage;
        if (lightLevel <= 7) { // Monsters can spawn at light level 7 or lower
            monsterSpawnMessage = languageConfig.getString("monster-can-spawn-message", "&cMonsters can spawn at this light level of %s.");
        } else {
            monsterSpawnMessage = languageConfig.getString("monster-cannot-spawn-message", "&aMonsters cannot spawn at this light level of %s.");
        }

        // Replace the placeholder with the actual light level
        monsterSpawnMessage = String.format(monsterSpawnMessage, lightLevel);

        // Check the configuration to see whether to display the message in the action bar or in the chat
        boolean displayInActionbar = getConfig().getBoolean("display-in-actionbar", true);
        if (displayInActionbar) {
            // Display the message in the action bar
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(translateColorCodes(monsterSpawnMessage)));
        } else {
            // Display the message in the chat
            player.sendMessage(translateColorCodes(monsterSpawnMessage));
        }
    }

    public static String translateColorCodes(String message) {
        // Translate Bukkit color codes (denoted with '&') into actual color codes
        String translated = ChatColor.translateAlternateColorCodes('&', message);

        // Define a regular expression pattern that matches hex color codes (denoted with '&#')
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

        // Create a matcher that will match the given pattern in the translated message
        Matcher matcher = hexPattern.matcher(translated);

        // Create a new buffer with sufficient initial capacity to avoid reallocation
        // A hex color code requires 14 characters, so assuming the worst case that all codes are hex codes
        // Each character in the original message is either a normal character (requiring one character in the buffer)
        // or part of a hex code (requiring 14/7 = 2 characters in the buffer)
        StringBuffer buffer = new StringBuffer(translated.length() + 4 * 8);

        // Find all hex color codes in the translated message
        while (matcher.find()) {
            // Get the actual hex code (without the '&#' prefix)
            String group = matcher.group(1);

            // Replace the found hex code with the corresponding color code
            // A color code consists of the color character (ChatColor.COLOR_CHAR) followed by 'x',
            // and then pairs of color characters and hex code characters
            matcher.appendReplacement(buffer, ChatColor.COLOR_CHAR + "x" +
                    ChatColor.COLOR_CHAR + group.charAt(0) + ChatColor.COLOR_CHAR + group.charAt(1) +
                    ChatColor.COLOR_CHAR + group.charAt(2) + ChatColor.COLOR_CHAR + group.charAt(3) +
                    ChatColor.COLOR_CHAR + group.charAt(4) + ChatColor.COLOR_CHAR + group.charAt(5)
            );
        }

        // Append the remainder of the translated message to the buffer and convert the buffer to a string
        return matcher.appendTail(buffer).toString();
    }

    public void reloadLanguageConfig() {
        // Reload the language config file when called
        String languageCode = getConfig().getString("language-code", "en");
        if (languageConfigFile == null) {
            languageConfigFile = new File(getDataFolder() + "/languages", languageCode + ".yml");
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageConfigFile);

        // Look for defaults in the jar
        InputStream defConfigStream = this.getResource("languages/" + languageCode + ".yml");
        if (defConfigStream != null) {
            Reader reader = new InputStreamReader(defConfigStream);
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
            languageConfig.setDefaults(defConfig);
        }
    }
}
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LightLevelDisplay extends JavaPlugin implements CommandExecutor, Listener {

    // Store a map of player UUIDs to boolean values, indicating whether light level display is enabled for each player
    private final Map<UUID, Boolean> lightLevelDisplayMap = new HashMap<>();

    @Override
    public void onEnable() {
        // Register the command executor and event listener when the plugin is enabled
        this.getCommand("lightlevel").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        // Save the default configuration file if it does not already exist
        this.saveDefaultConfig();

        Metrics metrics = new Metrics(this, 18811);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("not-a-player-message", "&cThis command can only be used by players.")));
            return false;
        }

        Player player = (Player) sender;

        boolean checkPermission = getConfig().getBoolean("check-permission", true);
        if (checkPermission && !player.hasPermission(getConfig().getString("permission-node", "lightdisplay"))) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("no-permission-message", "&cYou don't have permission to use this command.")));
            return false;
        }

        UUID playerUUID = player.getUniqueId();

        boolean displayLightLevel = lightLevelDisplayMap.getOrDefault(playerUUID, false);
        lightLevelDisplayMap.put(playerUUID, !displayLightLevel);

        String message = !displayLightLevel ?
                getConfig().getString("light-level-enabled", "&aLight level display has been enabled.") :
                getConfig().getString("light-level-disabled", "&cLight level display has been disabled.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

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
            monsterSpawnMessage = getConfig().getString("monster-can-spawn-message", "&cMonsters can spawn at this light level of %s.");
        } else {
            monsterSpawnMessage = getConfig().getString("monster-cannot-spawn-message", "&aMonsters cannot spawn at this light level of %s.");
        }

        // Replace the placeholder with the actual light level
        monsterSpawnMessage = String.format(monsterSpawnMessage, lightLevel);

        // Check the configuration to see whether to display the message in the action bar or in the chat
        boolean displayInActionbar = getConfig().getBoolean("display-in-actionbar", true);
        if (displayInActionbar) {
            // Display the message in the action bar
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', monsterSpawnMessage)));
        } else {
            // Display the message in the chat
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', monsterSpawnMessage));
        }
    }
}
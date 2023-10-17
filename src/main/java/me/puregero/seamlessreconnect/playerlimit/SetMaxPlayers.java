package me.puregero.seamlessreconnect.playerlimit;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.SeamlessReconnect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class SetMaxPlayers extends Command implements Listener {
    private final String SET_MAX_PLAYERS_CHANNEL = "seamlessreconnect:setmaxplayers";
    private final String UPDATE_MAX_PLAYERS_PLUGIN_CHANNEL = "seamlessreconnect:updatemaxplayers";
    private final SeamlessReconnect plugin;
    private int maxPlayers = 0;

    public SetMaxPlayers(SeamlessReconnect plugin) {
        super("srsetmaxplayers");
        setPermission("seamlessreconnect.srsetmaxplayers");
        this.plugin = plugin;

        MultiLib.onString(this.plugin, SET_MAX_PLAYERS_CHANNEL, string -> {
            if (string.isEmpty()) {
                setMaxPlayers(maxPlayers, true);
            } else {
                setMaxPlayers(Integer.parseInt(string), false);
            }
        });

        MultiLib.notify(SET_MAX_PLAYERS_CHANNEL, ""); // Request the current max players count

        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, UPDATE_MAX_PLAYERS_PLUGIN_CHANNEL);

        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", this);
    }

    private void setMaxPlayers(int maxPlayers, boolean broadcast) {
        this.maxPlayers = maxPlayers;

        if (broadcast) {
            MultiLib.notify(SET_MAX_PLAYERS_CHANNEL, Integer.toString(maxPlayers));
        }

        for (Player player : MultiLib.getLocalOnlinePlayers()) {
            sendPluginMessage(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendPluginMessage(event.getPlayer());
    }

    private void sendPluginMessage(Player player) {
        player.sendPluginMessage(this.plugin, UPDATE_MAX_PLAYERS_PLUGIN_CHANNEL, Integer.toString(maxPlayers).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("seamlessreconnect.srsetmaxplayers")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("The current player limit is " + maxPlayers).color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /srsetmaxplayers <method>").color(NamedTextColor.RED));
            return false;
        }

        try {
            setMaxPlayers(Integer.parseInt(args[0]), true);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[0]).color(NamedTextColor.RED));
            return false;
        }

        Bukkit.broadcast(Component.text("[" + sender.getName() + "] Set max players to " + args[0]).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), "seamlessreconnect.srsetmaxplayers");
        sender.sendMessage(Component.text("Set max players to " + maxPlayers).color(NamedTextColor.GREEN));
        return true;
    }
}

package me.puregero.seamlessreconnect;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;

import java.util.Locale;

public class PlayerListener implements Listener {
    private final SeamlessReconnect plugin;

    public PlayerListener(SeamlessReconnect plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // On a player join, register a packet listener to their connection
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ((CraftPlayer) player).getHandle().connection.connection.channel.pipeline().addBefore("packet_handler", "seamlessreconnect", new PacketListener(plugin, player));
    }

    // On a player kick when they are being sent to another server, send necessary packets to prevent glitches
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (PlainTextComponentSerializer.plainText().serialize(event.reason()).toLowerCase(Locale.ROOT).startsWith("sendto:")) {
            Player player = event.getPlayer();
            PacketListener packetListener = (PacketListener) ((CraftPlayer) player).getHandle().connection.connection.channel.pipeline().get("seamlessreconnect");
            packetListener.sendDismount();
        }
    }
}

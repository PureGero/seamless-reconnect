package me.puregero.seamlessreconnect;

import com.github.puregero.multilib.MultiLib;
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
        Player player = event.getPlayer();
        if (PlainTextComponentSerializer.plainText().serialize(event.reason()).toLowerCase(Locale.ROOT).startsWith("sendto:")) {
            plugin.broadcast("reconnecting", player.getUniqueId() + "\t" + player.getEntityId());
            PacketListener packetListener = ((CraftPlayer) player).getHandle().connection.connection.channel.pipeline().get(PacketListener.class);
//            packetListener.sendDismount();
        }
    }
}

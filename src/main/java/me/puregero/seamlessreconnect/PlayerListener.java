package me.puregero.seamlessreconnect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

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

        if (plugin.reconnectingPlayers.contains(event.getPlayer().getUniqueId())) {
            event.joinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.reconnectingPlayers.contains(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    // On a player kick when they are being sent to another server, send necessary packets to prevent glitches
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.reason());
        if (message.startsWith("sendto:")) {
            player.sendActionBar(Component.text("Connecting to " + message.split("sendto:")[1] + "...").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            plugin.broadcast("reconnecting", player.getUniqueId() + "\t" + player.getEntityId());
//            PacketListener packetListener = ((CraftPlayer) player).getHandle().connection.connection.channel.pipeline().get(PacketListener.class);
        }
    }

    @EventHandler
    public void onVehicleMount(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && containsPlayer(event.getVehicle())) {
            player.sendActionBar(Component.text("Only one player can use a boat at a time (sorry!!)").color(NamedTextColor.RED));
            event.setCancelled(true);
        }
    }

    private boolean containsPlayer(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player || containsPlayer(passenger)) {
                return true;
            }
        }

        return false;
    }
}

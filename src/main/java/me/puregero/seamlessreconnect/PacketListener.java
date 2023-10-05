package me.puregero.seamlessreconnect;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PacketListener extends ChannelDuplexHandler {
    private final SeamlessReconnect plugin;
    private final Player player;

    private Optional<Integer> vehicle = Optional.empty();

    public PacketListener(SeamlessReconnect plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ClientboundSetPassengersPacket setPassengersPacket) {
            // Check if the player's id is in setPassengersPacket.getPassengers()
            if (ArrayUtils.contains(setPassengersPacket.getPassengers(), player.getEntityId())) {
                vehicle = Optional.of(setPassengersPacket.getVehicle());
            } else if (vehicle.filter(id -> setPassengersPacket.getVehicle() == id).isPresent()) {
                vehicle = Optional.empty();
            }
        }

        if (msg instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
            // Remove certain entities after 1 second instead of immediately in case they are re-added (eg a player seamlessly reconnecting)
            Set<Integer> delayed = removeEntitiesPacket.getEntityIds().stream().filter(plugin.delayRemovalPacket::contains).collect(Collectors.toSet());
            if (!delayed.isEmpty()) {
                removeEntitiesPacket.getEntityIds().removeAll(delayed);
                delayed.forEach(entityId -> plugin.runLater(() -> {
                    if (((CraftWorld) this.player.getWorld()).getHandle().getEntity(entityId) == null) {
                        // This entity wasn't re-added, remove it
                        ((CraftPlayer) this.player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(entityId));
                    }
                }, 20));
            }
        }

        super.write(ctx, msg, promise);
    }

    public void sendDismount() {
        if (vehicle.isPresent()) {
            // Create our own packet manually cause the constructor we want doesn't exist :/
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
            friendlyByteBuf.writeVarInt(vehicle.get());
            friendlyByteBuf.writeVarIntArray(new int[0]);
            ((CraftPlayer) player).getHandle().connection.connection.send(new ClientboundSetPassengersPacket(friendlyByteBuf));
            friendlyByteBuf.release();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        super.channelRead(ctx, msg);
    }
}

package me.puregero.seamlessreconnect.kmeans;

import org.bukkit.entity.Player;

import java.util.UUID;

public class LocalPlayerLocation extends DistancePlayerLocation {

    private final UUID uuid;

    public LocalPlayerLocation(Player player) {
        super(player);
        this.uuid = player.getUniqueId();
    }

    public LocalPlayerLocation(String uuid, int x, int z) {
        super(x, z);
        this.uuid = UUID.fromString(uuid);
    }

    public UUID uuid() {
        return uuid;
    }
}

package me.puregero.seamlessreconnect.kmeans;

import org.bukkit.entity.Player;

import java.util.UUID;

public class LocalPlayerLocation extends PlayerLocation {

    private final UUID uuid;
    private String assignedServer;
    private double distance;

    public LocalPlayerLocation(Player player) {
        super(player);
        this.uuid = player.getUniqueId();
    }

    public UUID uuid() {
        return uuid;
    }

    public String assignedServer() {
        return assignedServer;
    }

    public void assignedServer(String server) {
        this.assignedServer = server;
    }

    public double distance() {
        return distance;
    }

    public void distance(double distance) {
        this.distance = distance;
    }
}

package me.puregero.seamlessreconnect.kmeans;

import org.bukkit.entity.Player;

public class DistancePlayerLocation extends PlayerLocation {

    private double closestDistance;
    private String closestServer;

    public DistancePlayerLocation(Player player) {
        super(player);
    }

    public DistancePlayerLocation(PlayerLocation playerLocation) {
        super(playerLocation.x(), playerLocation.z());
    }

    public DistancePlayerLocation(int x, int z) {
        super(x, z);
    }

    public double closestDistance() {
        return closestDistance;
    }

    public String closestServer() {
        return closestServer;
    }

    public void closestDistance(double closestDistance) {
        this.closestDistance = closestDistance;
    }

    public void closestServer(String closestServer) {
        this.closestServer = closestServer;
    }
}

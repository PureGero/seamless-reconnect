package me.puregero.seamlessreconnect.kmeans;

import java.io.Serializable;

public record UpdatePacket(String serverName, PlayerLocation[] playerLocations, int centerX, int centerZ) implements Serializable {

}

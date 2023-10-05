package me.puregero.seamlessreconnect.kmeans;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.SeamlessReconnect;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ServerAllocationSystem {

    private static final String CHANNEL = "kmeans:update";

    private final SeamlessReconnect plugin;
    final Map<String, UpdatePacket> servers = new ConcurrentHashMap<>();

    int centerX = (int) (Math.random() * 1000) - 500;
    int centerZ = (int) (Math.random() * 1000) - 500;

    public ServerAllocationSystem(SeamlessReconnect plugin) {
        this.plugin = plugin;

        MultiLib.on(plugin, CHANNEL, bytes -> {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                handleUpdate((UpdatePacket) in.readObject());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new DebugCommand(this));

        this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, this::tick, 20 * 5, 20 * 5);

        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, "seamlessreconnect:initialserverquery");
        this.plugin.getServer().getMessenger().registerIncomingPluginChannel(this.plugin, "seamlessreconnect:initialserverquery", (channel, player, bytes) -> {
            String uuid = new String(bytes, StandardCharsets.UTF_8);
            PlayerDatReader.getCoords(uuid).thenAccept(location -> {
                if (location.isEmpty()) location = Optional.of(Bukkit.getWorlds().get(0).getSpawnLocation());
                List<UpdatePacket> servers = new ArrayList<>(this.servers.size() + 1);
                servers.add(new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));
                servers.addAll(this.servers.values());

                double minDistance = Double.MAX_VALUE;
                String assignedServer = null;
                int x = location.get().getBlockX();
                int z = location.get().getBlockZ();

                for (UpdatePacket updatePacket : servers) {
                    double distance = Math.pow(x - updatePacket.centerX(), 2) + Math.pow(z - updatePacket.centerZ(), 2);
                    if (distance < minDistance) {
                        minDistance = distance;
                        assignedServer = updatePacket.serverName();
                    }
                }

                if (assignedServer == null) {
                    assignedServer = MultiLib.getLocalServerName();
                }

                player.sendPluginMessage(plugin, "seamlessreconnect:initialserverquery", (uuid + "\t" + assignedServer).getBytes(StandardCharsets.UTF_8));
            });
        });
    }

    private void tick() {
        List<LocalPlayerLocation> localPlayersToMove = new ArrayList<>();
        List<PlayerLocation> playerLocations = new ArrayList<>();
        Map<String, UpdatePacket> serverList = new HashMap<>();

        serverList.put(MultiLib.getLocalServerName(), new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));

        servers.values().forEach(updatePacket -> {
            serverList.put(updatePacket.serverName(), updatePacket);
            playerLocations.addAll(Arrays.asList(updatePacket.playerLocations()));
        });

        MultiLib.getLocalOnlinePlayers().forEach(player -> playerLocations.add(new LocalPlayerLocation(player)));

        int ourIndex = 0;
        for (String serverName : serverList.keySet()) {
            if (serverName.equals(MultiLib.getLocalServerName())) {
                break;
            }
            ourIndex++;
        }

        int totalX = 0;
        int totalZ = 0;
        int count = 0;

        // Calculate each player's closest server
        for (PlayerLocation playerLocation : playerLocations) {
            double minDistance = Double.MAX_VALUE;
            String assignedServer = null;

            for (UpdatePacket updatePacket : serverList.values()) {
                double distance = Math.pow(playerLocation.x() - updatePacket.centerX(), 2) + Math.pow(playerLocation.z() - updatePacket.centerZ(), 2);
                if (distance < minDistance) {
                    minDistance = distance;
                    assignedServer = updatePacket.serverName();
                }
            }

            if (assignedServer != null) {
                // This is our player and they need to go to another server
                if (playerLocation instanceof LocalPlayerLocation localPlayerLocation && !assignedServer.equals(MultiLib.getLocalServerName())) {
                    localPlayerLocation.distance(minDistance);
                    localPlayerLocation.assignedServer(assignedServer);
                    localPlayersToMove.add(localPlayerLocation);
                }

                // This player has been assigned to our server, add its location to our average
                if (assignedServer.equals(MultiLib.getLocalServerName())) {
                    totalX += playerLocation.x();
                    totalZ += playerLocation.z();
                    count++;
                }
            }
        }

        if (count > 0) {
            // Calculate our location
            centerX = totalX / count;
            centerZ = totalZ / count;
        } else if (ourIndex * 2 < playerLocations.size()) {
            // We have no players, move closer to our closest player
            double closestDistance = Double.MAX_VALUE;
            PlayerLocation closestPlayer = null;

            for (PlayerLocation playerLocation : playerLocations) {
                double distance = Math.pow(playerLocation.x() - centerX, 2) + Math.pow(playerLocation.z() - centerZ, 2);
                if (distance < 128 * 128) {
                    // We're too close, abort (if too many servers are close to the same target, it can cause annoying ping-ponging)
                    closestPlayer = null;
                    break;
                } else if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = playerLocation;
                }
            }

            if (closestPlayer != null) {
                // Move progressively closer to the closest player
                centerX += (closestPlayer.x() - centerX) / 2;
                centerZ += (closestPlayer.z() - centerZ) / 2;
            }
        }

        if (MultiLib.getLocalServerName().equals("server1")) {
            centerX = 500;
            centerZ = 0;
        } else if (MultiLib.getLocalServerName().equals("server2")) {
            centerX = -500;
            centerZ = 0;
        }

        plugin.getLogger().info(ourIndex + ": Center is " + centerX + ", " + centerZ);

        sendUpdate();

        Bukkit.getScheduler().runTask(plugin, () -> movePlayers(localPlayersToMove));
    }

    private void sendUpdate() {
        String serverName = MultiLib.getLocalServerName();
        PlayerLocation[] playerLocations = MultiLib.getLocalOnlinePlayers().stream()
                .map(PlayerLocation::new)
                .toArray(PlayerLocation[]::new);

        UpdatePacket updatePacket = new UpdatePacket(
                serverName,
                playerLocations,
                centerX,
                centerZ);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)){
            out.writeObject(updatePacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MultiLib.notify(CHANNEL, buffer.toByteArray());
    }

    private void movePlayers(List<LocalPlayerLocation> localPlayersToMove) {
        // Move the player that's closest to their newly assigned server
        double closestDistance = Double.MAX_VALUE;
        LocalPlayerLocation closestPlayer = null;
        Player closestBukkitPlayer = null;

        // First find a server to send them to with less players than us
        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
            if (localPlayerLocation.distance() < closestDistance && player != null && MultiLib.isLocalPlayer(player) && hasLessPlayers(localPlayerLocation.assignedServer())) {
                closestDistance = localPlayerLocation.distance();
                closestPlayer = localPlayerLocation;
                closestBukkitPlayer = player;
            }
        }

        // Then try sending players to fuller servers
        if (closestPlayer == null) {
            for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
                Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
                if (localPlayerLocation.distance() < closestDistance && player != null && MultiLib.isLocalPlayer(player)) {
                    closestDistance = localPlayerLocation.distance();
                    closestPlayer = localPlayerLocation;
                    closestBukkitPlayer = player;
                }
            }
        }

        if (closestBukkitPlayer != null) {
            closestBukkitPlayer.kick(Component.text("sendto:" + closestPlayer.assignedServer()));
        }
    }

    private boolean hasLessPlayers(String server) {
        return servers.get(server).playerLocations().length < MultiLib.getLocalOnlinePlayers().size() + 3; // Don't send players to an overwhelmed server
    }

    private void handleUpdate(UpdatePacket updatePacket) {
        servers.put(updatePacket.serverName(), updatePacket);
    }

}

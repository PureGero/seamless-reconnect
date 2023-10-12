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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerAllocationSystem_EqualPlayerCounts {

    private static final String CHANNEL = "kmeans:update";

    private final SeamlessReconnect plugin;
    private final Map<String, UpdatePacket> servers = new ConcurrentHashMap<>();

    private int centerX = (int) (Math.random() * 1000);
    private int centerZ = (int) (Math.random() * 1000);

    public ServerAllocationSystem_EqualPlayerCounts(SeamlessReconnect plugin) {
        this.plugin = plugin;

        MultiLib.on(plugin, CHANNEL, bytes -> {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                handleUpdate((UpdatePacket) in.readObject());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, this::tick, 20 * 5, 20 * 5);
    }

    private void tick() {
        List<LocalPlayerLocation> localPlayersToMove = new ArrayList<>();
        Set<PlayerLocation> playerLocations = new LinkedHashSet<>();
        Set<UpdatePacket> serverList = new LinkedHashSet<>(); // Ordered set

        serverList.add(new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));

        servers.values().forEach(updatePacket -> {
            serverList.add(updatePacket);
            playerLocations.addAll(Arrays.asList(updatePacket.playerLocations()));
        });

        MultiLib.getLocalOnlinePlayers().forEach(player -> playerLocations.add(new LocalPlayerLocation(player)));

        int totalX = 0;
        int totalZ = 0;
        int count = 0;

        while (!playerLocations.isEmpty()) {
            for (UpdatePacket server : serverList) {
                PlayerLocation closest = null;
                double closestDistance = Double.MAX_VALUE;

                for (PlayerLocation playerLocation : playerLocations) {
                    double distance = Math.pow(playerLocation.x() - server.centerX(), 2) + Math.pow(playerLocation.z() - server.centerZ(), 2);
                    if (distance < closestDistance) {
                        closest = playerLocation;
                        closestDistance = distance;
                    }
                }

                if (closest != null) {
                    playerLocations.remove(closest);

                    if (closest instanceof LocalPlayerLocation localPlayerLocation && !server.serverName().equals(MultiLib.getLocalServerName())) {
                        localPlayerLocation.closestServer(server.serverName());
                        localPlayersToMove.add(localPlayerLocation);
                    }
                }

                if (server.serverName().equals(MultiLib.getLocalServerName()) && closest != null) {
                    totalX += closest.x();
                    totalZ += closest.z();
                    count++;
                }
            }
        }

        if (count > 0) {
            centerX = totalX / count;
            centerZ = totalZ / count;
        }

        plugin.getLogger().info("Center is " + centerX + ", " + centerZ);

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
        // Shuffle
        Collections.shuffle(localPlayersToMove);

        // Move a player already in the external chunk
        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
            if (player != null && MultiLib.isLocalPlayer(player) && MultiLib.isChunkExternal(player) && hasLessPlayers(localPlayerLocation.closestServer())) {
                String serverName = localPlayerLocation.closestServer();
                player.kick(Component.text("sendto:" + serverName));
                return;
            }
        }

        // Otherwise, just move a player
        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
            if (player != null && MultiLib.isLocalPlayer(player) && hasLessPlayers(localPlayerLocation.closestServer())) {
                String serverName = localPlayerLocation.closestServer();
                player.kick(Component.text("sendto:" + serverName));
                return;
            }
        }
    }

    private boolean hasLessPlayers(String server) {
        return servers.get(server).playerLocations().length < MultiLib.getLocalOnlinePlayers().size() + 3; // Don't send players to an overwhelmed server
    }

    private void handleUpdate(UpdatePacket updatePacket) {
        servers.put(updatePacket.serverName(), updatePacket);
    }

}

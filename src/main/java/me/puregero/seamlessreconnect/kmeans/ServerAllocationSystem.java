package me.puregero.seamlessreconnect.kmeans;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.SeamlessReconnect;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerAllocationSystem {

    private static final String CHANNEL = "kmeans:update";
    private static final String CHANNEL_UPDATE_RATE = "kmeans:set_update_rate";

    private final SeamlessReconnect plugin;
    final Map<String, UpdatePacket> servers = new ConcurrentHashMap<>();
    private int updateRate = 2 * 20;
    private BukkitTask updateTask = null;

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

        MultiLib.onString(plugin, CHANNEL_UPDATE_RATE, str -> handleSetUpdateRate(Integer.parseInt(str)));

        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new DebugCommand(this));
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new SetUpdateRate(this));

        handleSetUpdateRate(updateRate);

        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, "seamlessreconnect:initialserverquery");
        this.plugin.getServer().getMessenger().registerIncomingPluginChannel(this.plugin, "seamlessreconnect:initialserverquery", (channel, player, bytes) -> {
            String uuid = new String(bytes, StandardCharsets.UTF_8);
            PlayerDatReader.getCoords(uuid).thenAccept(location -> {
                if (location.isEmpty()) location = Optional.of(Bukkit.getWorlds().get(0).getSpawnLocation());

                LocalPlayerLocation thisPlayer = new LocalPlayerLocation(uuid, location.get().getBlockX(), location.get().getBlockZ());
                List<LocalPlayerLocation> localPlayers = MultiLib.getLocalOnlinePlayers().stream().map(LocalPlayerLocation::new).toList();
                List<DistancePlayerLocation> playerLocations = new ArrayList<>(localPlayers);
                Map<String, UpdatePacket> serverList = new HashMap<>();

                serverList.put(MultiLib.getLocalServerName(), new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));

                servers.values().forEach(updatePacket -> {
                    serverList.put(updatePacket.serverName(), updatePacket);
                    for (PlayerLocation playerLocation : updatePacket.playerLocations()) {
                        playerLocations.add(new DistancePlayerLocation(playerLocation));
                    }
                });

                playerLocations.add(thisPlayer);

                assignPlayersToServers(playerLocations, serverList.values());

                player.sendPluginMessage(plugin, "seamlessreconnect:initialserverquery", (uuid + "\t" + thisPlayer.closestServer()).getBytes(StandardCharsets.UTF_8));
            });
        });
    }

    public void setUpdateRate(int rate) {
        MultiLib.notify(CHANNEL_UPDATE_RATE, Integer.toString(rate));
        handleSetUpdateRate(rate);
    }

    private void handleSetUpdateRate(int rate) {
        this.updateRate = rate;

        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::tick, updateRate);
    }

    private PriorityQueue<DistancePlayerLocation> calculateClosestServers(Collection<DistancePlayerLocation> players, String serverToRecalculate, Collection<UpdatePacket> servers) {
        // A queue where the player furthest from their assigned server is at the front
        PriorityQueue<DistancePlayerLocation> closestServers = new PriorityQueue<>(Comparator.comparingDouble(location -> -location.closestDistance()));

        for (DistancePlayerLocation player : players) {
            if (player.closestServer() == null || player.closestServer().equals(serverToRecalculate)) {
                double minDistance = Double.MAX_VALUE;
                String assignedServer = null;

                for (UpdatePacket updatePacket : servers) {
                    double distance = Math.pow(player.x() - updatePacket.centerX(), 2) + Math.pow(player.z() - updatePacket.centerZ(), 2);
                    if (distance < minDistance) {
                        minDistance = distance;
                        assignedServer = updatePacket.serverName();
                    }
                }

                if (assignedServer != null) {
                    player.closestDistance(minDistance);
                    player.closestServer(assignedServer);
                    closestServers.add(player);
                }
            }
        }

        return closestServers;
    }

    private void assignPlayersToServers(Collection<DistancePlayerLocation> playerLocations, Collection<UpdatePacket> serversList) {
        Set<UpdatePacket> servers = new HashSet<>(serversList);
        Queue<DistancePlayerLocation> queue = calculateClosestServers(playerLocations, null, servers);
        Map<String, List<DistancePlayerLocation>> serverPlayers = new HashMap<>();

        while (!queue.isEmpty()) {
            DistancePlayerLocation player = queue.poll();
            String assignedServer = player.closestServer();
            List<DistancePlayerLocation> list = serverPlayers.computeIfAbsent(assignedServer, k -> new ArrayList<>());

            int maxPlayers = assignedServer.equals(MultiLib.getLocalServerName()) ?
                    (int) Math.ceil(playerLocations.size() * 1.1 / serversList.size()) :
                    (int) Math.ceil(playerLocations.size() * 1.0 / serversList.size());

            if (list.size() < maxPlayers) {
                list.add(player);
            } else {
                queue.add(player);
                servers.removeIf(updatePacket -> updatePacket.serverName().equals(assignedServer));
                queue = calculateClosestServers(queue, assignedServer, servers);
            }
        }
    }

    private void tick() {
        updateTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::tick, updateRate);

        List<LocalPlayerLocation> localPlayers = MultiLib.getLocalOnlinePlayers().stream().map(LocalPlayerLocation::new).toList();
        List<DistancePlayerLocation> playerLocations = new ArrayList<>(localPlayers);
        Map<String, UpdatePacket> serverList = new HashMap<>();

        serverList.put(MultiLib.getLocalServerName(), new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));

        servers.values().forEach(updatePacket -> {
            serverList.put(updatePacket.serverName(), updatePacket);
            for (PlayerLocation playerLocation : updatePacket.playerLocations()) {
                playerLocations.add(new DistancePlayerLocation(playerLocation));
            }
        });

        assignPlayersToServers(playerLocations, serverList.values());

        int totalX = 0;
        int totalZ = 0;
        int count = 0;
        List<LocalPlayerLocation> localPlayersToMove = new ArrayList<>();
        for (DistancePlayerLocation playerLocation : playerLocations) {
            if (playerLocation instanceof LocalPlayerLocation localPlayerLocation && !localPlayerLocation.closestServer().equals(MultiLib.getLocalServerName())) {
                localPlayersToMove.add(localPlayerLocation);
            }
            if (playerLocation.closestServer().equals(MultiLib.getLocalServerName())) {
                totalX += playerLocation.x();
                totalZ += playerLocation.z();
                count += 1;
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
        // Move the player that's closest to their newly assigned server
        double closestDistance = Double.MAX_VALUE;
        LocalPlayerLocation closestPlayer = null;
        Player closestBukkitPlayer = null;

        // First find a server to send them to with less players than us
        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
            if (localPlayerLocation.closestDistance() < closestDistance && player != null && MultiLib.isLocalPlayer(player) && hasLessPlayers(localPlayerLocation.closestServer())) {
                closestDistance = localPlayerLocation.closestDistance();
                closestPlayer = localPlayerLocation;
                closestBukkitPlayer = player;
            }
        }

        // Then try sending players to fuller servers
        if (closestPlayer == null) {
            for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
                Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
                if (localPlayerLocation.closestDistance() < closestDistance && player != null && MultiLib.isLocalPlayer(player)) {
                    closestDistance = localPlayerLocation.closestDistance();
                    closestPlayer = localPlayerLocation;
                    closestBukkitPlayer = player;
                }
            }
        }

        if (closestBukkitPlayer != null) {
            closestBukkitPlayer.kick(Component.text("sendto:" + closestPlayer.closestServer()));
        }
    }

    private boolean hasLessPlayers(String server) {
        return servers.get(server).playerLocations().length < MultiLib.getLocalOnlinePlayers().size() + 3; // Prefer to not send players to an overwhelmed server
    }

    private void handleUpdate(UpdatePacket updatePacket) {
        servers.put(updatePacket.serverName(), updatePacket);
    }
}

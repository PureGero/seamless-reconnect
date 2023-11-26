package me.puregero.seamlessreconnect.kmeans;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.SeamlessReconnect;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import puregero.multipaper.MultiPaper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ServerAllocationSystem {

    private static final String CHANNEL = "kmeans:update";
    private static final String CHANNEL_UPDATE_RATE = "kmeans:set_update_rate";
    private static final String CHANNEL_CENTROID_METHOD = "kmeans:set_centroid_method";
    private static final String CHANNEL_START = "kmeans:start";
    private static final String CHANNEL_CPU = "kmeans:set_cpus";

    private final SeamlessReconnect plugin;
    final Map<String, UpdatePacket> servers = new ConcurrentHashMap<>();
    private int updateRate = 5 * 20;
    private BukkitTask updateTask = null;
    private CentroidMethod centroidMethod = CentroidMethod.DYNAMIC_LOCAL;
    public Map<String, String> cpus = new HashMap<>();

    int centerX = (int) (Math.random() * 1000) - 500;
    int centerZ = (int) (Math.random() * 1000) - 500;

    public ServerAllocationSystem(SeamlessReconnect plugin) {
        this.plugin = plugin;

        Bukkit.setMaxPlayers(1000); // All the players belong to us

        MultiLib.on(plugin, CHANNEL, bytes -> {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                handleUpdate((UpdatePacket) in.readObject());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        MultiLib.onString(plugin, CHANNEL_START, string -> {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Process process = Runtime.getRuntime().exec("lscpu");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        reader.lines().filter(line -> line.startsWith("Model name:")).forEach(line -> {
                            String model = line.substring(12).trim();
                            MultiLib.notify(CHANNEL_CPU, MultiLib.getLocalServerName() + "\t" + model);
                            cpus.put(MultiLib.getLocalServerName(), model);
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });

        MultiLib.onString(plugin, CHANNEL_CPU, string -> {
            String[] split = string.split("\t");
            cpus.put(split[0], split[1]);
        });

        MultiLib.notify(CHANNEL_START, MultiLib.getLocalServerName());

        MultiLib.onString(plugin, CHANNEL_UPDATE_RATE, str -> handleSetUpdateRate(Integer.parseInt(str)));
        MultiLib.onString(plugin, CHANNEL_CENTROID_METHOD, this::handleSetCentroidMethod);

        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new DebugCommand(this));
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new SetUpdateRate(this));
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new SetCentroidMethod(this));
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", new GetCpus(this));

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

    public void setCentroidMethod(String method) {
        MultiLib.notify(CHANNEL_CENTROID_METHOD, method);
        handleSetCentroidMethod(method);
    }

    private void handleSetCentroidMethod(String method) {
        try {
            centroidMethod = CentroidMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Tried to set centroidMethod to an unknown method of: " + method, e);
        }
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
                    if (player instanceof LocalPlayerLocation && updatePacket.serverName().equals(MultiLib.getLocalServerName())) {
                        distance -= 8 * 8; // Prefer not to send our players to other servers
                    }
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

        int ourIndex = 0;
        for (String serverName : serverList.keySet()) {
            if (serverName.equals(MultiLib.getLocalServerName())) {
                break;
            }
            ourIndex++;
        }

        Centroid centroid = centroidMethod.updateBeforeAssignment(playerLocations, serverList.values(), new Centroid(centerX, centerZ), ourIndex);
        if (centroid != null) {
            centerX = centroid.x();
            centerZ = centroid.z();
        }

        // Update out centerX and centerZ
        serverList.put(MultiLib.getLocalServerName(), new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], centerX, centerZ));

        assignPlayersToServers(playerLocations, serverList.values());

        Centroid centroid2 = centroidMethod.updateAfterAssignment(playerLocations, serverList.values(), new Centroid(centerX, centerZ), ourIndex);
        if (centroid2 != null) {
            centerX = centroid2.x();
            centerZ = centroid2.z();
        }

        List<LocalPlayerLocation> localPlayersToMove = new ArrayList<>();
        for (DistancePlayerLocation playerLocation : playerLocations) {
            if (playerLocation instanceof LocalPlayerLocation localPlayerLocation && !localPlayerLocation.closestServer().equals(MultiLib.getLocalServerName())) {
                localPlayersToMove.add(localPlayerLocation);
            }
        }

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
        int maxRecursion = 0;

        List<Integer> playerCounts = servers.values().stream()
                .filter(server -> MultiPaper.getConnection().getOrCreateServer(server.serverName()).isAlive())
                .map(server -> server.playerLocations().length).toList();

        if (!playerCounts.isEmpty()) {
            int averagePlayerCount = playerCounts.stream().reduce(0, Integer::sum) / playerCounts.size();
            int myPlayerCount = MultiLib.getLocalOnlinePlayers().size();
            int difference = myPlayerCount - averagePlayerCount;
            if (difference > 0) {
                maxRecursion = difference / 10;
            }
        }

        movePlayers(localPlayersToMove, 0, maxRecursion);
    }

    private double calcDistanceFromUs(PlayerLocation playerLocation) {
        return Math.pow(playerLocation.x() - centerX, 2) + Math.pow(playerLocation.z() - centerZ, 2);
    }

    private void movePlayers(List<LocalPlayerLocation> localPlayersToMove, int recursion, int maxRecursion) {
        // Move the player that's closest to their newly assigned server
        double furthestDistance = Double.MIN_VALUE;
        LocalPlayerLocation furthestPlayer = null;
        Player furthestBukkitPlayer = null;

        // First find a server to send them to with less players than us
        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
            if (calcDistanceFromUs(localPlayerLocation) > furthestDistance && player != null && MultiLib.isLocalPlayer(player) && isNotFucked(localPlayerLocation.closestServer()) && hasLessPlayers(localPlayerLocation.closestServer())) {
                furthestDistance = calcDistanceFromUs(localPlayerLocation);
                furthestPlayer = localPlayerLocation;
                furthestBukkitPlayer = player;
            }
        }

        // Then try sending players to fuller servers
        if (furthestPlayer == null) {
            for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
                Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
                if (calcDistanceFromUs(localPlayerLocation) > furthestDistance && player != null && MultiLib.isLocalPlayer(player) && isNotFucked(localPlayerLocation.closestServer())) {
                    furthestDistance = calcDistanceFromUs(localPlayerLocation);
                    furthestPlayer = localPlayerLocation;
                    furthestBukkitPlayer = player;
                }
            }
        }

        // First find a server to send them to with less players than us
//        for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
//            Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
//            if (localPlayerLocation.closestDistance() < closestDistance && player != null && MultiLib.isLocalPlayer(player) && isNotFucked(localPlayerLocation.closestServer()) && hasLessPlayers(localPlayerLocation.closestServer())) {
//                closestDistance = localPlayerLocation.closestDistance();
//                closestPlayer = localPlayerLocation;
//                closestBukkitPlayer = player;
//            }
//        }
//
//        // Then try sending players to fuller servers
//        if (closestPlayer == null) {
//            for (LocalPlayerLocation localPlayerLocation : localPlayersToMove) {
//                Player player = Bukkit.getPlayer(localPlayerLocation.uuid());
//                if (localPlayerLocation.closestDistance() < closestDistance && player != null && MultiLib.isLocalPlayer(player) && isNotFucked(localPlayerLocation.closestServer())) {
//                    closestDistance = localPlayerLocation.closestDistance();
//                    closestPlayer = localPlayerLocation;
//                    closestBukkitPlayer = player;
//                }
//            }
//        }

        if (furthestBukkitPlayer != null) {
            if (furthestDistance < 64 * 64) {
                // Too close to our center point, no point moving them
                return;
            }

            furthestBukkitPlayer.kick(Component.text("sendto:" + furthestPlayer.closestServer()));

            if (recursion < maxRecursion) {
                movePlayers(localPlayersToMove, recursion + 1, maxRecursion);
            }
        }
    }

    private boolean hasLessPlayers(String server) {
        return servers.get(server).playerLocations().length < MultiLib.getLocalOnlinePlayers().size() + 3; // Prefer to not send players to an overwhelmed server
    }

    private boolean isNotFucked(String server) {
        return MultiPaper.getConnection().getOrCreateServer(server).isAlive() // Check the server is online
                && servers.get(server).playerLocations().length < (MultiLib.getLocalOnlinePlayers().size() + 3) * 1.5; // Don't send players to servers with way too many players on them
    }

    private void handleUpdate(UpdatePacket updatePacket) {
        servers.put(updatePacket.serverName(), updatePacket);
    }
}

package me.puregero.seamlessreconnect;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.kmeans.DebugCommand;
import me.puregero.seamlessreconnect.kmeans.ServerAllocationSystem;
import me.puregero.seamlessreconnect.playerlimit.SetMaxPlayers;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SeamlessReconnect extends JavaPlugin {

    public final Set<Integer> delayRemovalPacket = new HashSet<>();
    public final Set<UUID> reconnectingPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        new PlayerListener(this);

        if (Boolean.parseBoolean(System.getProperty("sr.enabled", "true"))) {
            new ServerAllocationSystem(this);
        }

        new SetMaxPlayers(this);

        MultiLib.onString(this, "seamlessreconnect:reconnecting", message -> handleNotification("reconnecting", message));
    }

    public void runLater(Runnable runnable, int ticks) {
        this.getServer().getScheduler().runTaskLater(this, runnable, ticks);
    }

    public void broadcast(String channel, String message) {
        MultiLib.notify("seamlessreconnect:" + channel, message);
        handleNotification(channel, message);
    }

    private void handleNotification(String channel, String message) {
        if (channel.equals("reconnecting")) {
            String[] split = message.split("\t");
            UUID uuid = UUID.fromString(split[0]);
            int entityId = Integer.parseInt(split[1]);
            reconnectingPlayers.add(uuid);
            delayRemovalPacket.add(entityId);
            runLater(() -> {
                reconnectingPlayers.remove(uuid);
                delayRemovalPacket.remove(entityId);
            }, 50);
        }
    }
}

package me.puregero.seamlessreconnect;

import com.github.puregero.multilib.MultiLib;
import me.puregero.seamlessreconnect.kmeans.ServerAllocationSystem;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SeamlessReconnect extends JavaPlugin {

    public final Set<Integer> delayRemovalPacket = new HashSet<>();

    @Override
    public void onEnable() {
        new PlayerListener(this);
        new ServerAllocationSystem(this);

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
            delayRemovalPacket.add(entityId);
            runLater(() -> delayRemovalPacket.remove(entityId), 20);
        }
    }
}

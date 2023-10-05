package me.puregero.seamlessreconnect;

import me.puregero.seamlessreconnect.kmeans.ServerAllocationSystem;
import org.bukkit.plugin.java.JavaPlugin;

public class SeamlessReconnect extends JavaPlugin {
    @Override
    public void onEnable() {
        new PlayerListener(this);
        new ServerAllocationSystem(this);
    }
}

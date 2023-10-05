package me.puregero.seamlessreconnect.kmeans;

import org.bukkit.entity.Player;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class PlayerLocation implements Serializable {
    @Serial
    private static final long serialVersionUID = 0L;
    private final int x;
    private final int z;

    public PlayerLocation(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public PlayerLocation(Player player) {
        this(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PlayerLocation) obj;
        return this.x == that.x &&
                this.z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "PlayerLocation[" +
                "x=" + x + ", " +
                "z=" + z + ']';
    }


}

package me.puregero.seamlessreconnect.kmeans;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Location;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerDatReader {

    public static CompletableFuture<CompoundTag> readPlayerData(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return MinecraftServer.getServer().playerDataStorage.getPlayerData(uuid);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public static CompletableFuture<Optional<Location>> getCoords(String uuid) {
        return readPlayerData(uuid).thenApply(nbt -> {
            if (nbt == null || !nbt.contains("Pos")) return Optional.empty();

            ListTag pos = nbt.getList("Pos", 6);
            return Optional.of(new Location(null, pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)));
        });
    }
}

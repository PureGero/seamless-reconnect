package me.puregero.seamlessreconnect.kmeans;

import com.github.puregero.multilib.MultiLib;

import java.util.Collection;
import java.util.List;

public enum CentroidMethod {
    DYNAMIC(null, (playerLocations, servers, currentCentroid, index) -> {
        int x = 0;
        int z = 0;
        int count = 0;

        for (DistancePlayerLocation playerLocation : playerLocations) {
            if (playerLocation.closestServer().equals(MultiLib.getLocalServerName())) {
                x += playerLocation.x();
                z += playerLocation.z();
                count += 1;
            }
        }

        if (count == 0) {
            return null;
        } else {
            return new Centroid(x / count, z / count);
        }
    }),
    RADIAL((playerLocations, servers, currentCentroid, index) -> {
        // The centroids are so close to the center as the most distant players are allocated to centroids first, and the closest are allocated after all the centroids have become full
        double angle = (double) index / servers.size() * Math.PI * 2;
        return new Centroid(
                (int) (Math.cos(angle) * 50),
                (int) (Math.sin(angle) * 50)
        );
    }, null),
    DISTRIBUTED((playerLocations, servers, currentCentroid, index) -> {
        // The centroids are so close to the center as the most distant players are allocated to centroids first, and the closest are allocated after all the centroids have become full
        // Center piece
        if (index == 0) {
            return new Centroid(0, 0);
        }
        // Outer center
        if (index <= 4) {
            int count = Math.min(4, servers.size() - 1);
            double angle = (double) index / count * Math.PI * 2 + Math.PI / 4;
            return new Centroid(
                    (int) (Math.cos(angle) * 50),
                    (int) (Math.sin(angle) * 50)
            );
        }
        // Rest are radial
        double angle = (double) index / (servers.size() - 5) * Math.PI * 2;
        return new Centroid(
                (int) (Math.cos(angle) * 100),
                (int) (Math.sin(angle) * 100)
        );
    }, null);

    private final QuadFunction<List<DistancePlayerLocation>, Collection<UpdatePacket>, Centroid, Integer, Centroid> updateBeforeAssignment;
    private final QuadFunction<List<DistancePlayerLocation>, Collection<UpdatePacket>, Centroid, Integer, Centroid> updateAfterAssignment;

    CentroidMethod(QuadFunction<List<DistancePlayerLocation>, Collection<UpdatePacket>, Centroid, Integer, Centroid> updateBeforeAssignment, QuadFunction<List<DistancePlayerLocation>, Collection<UpdatePacket>, Centroid, Integer, Centroid> updateAfterAssignment) {
        this.updateBeforeAssignment = updateBeforeAssignment;
        this.updateAfterAssignment = updateAfterAssignment;
    }

    public Centroid updateBeforeAssignment(List<DistancePlayerLocation> playerLocations, Collection<UpdatePacket> servers, Centroid currentCentroid, int index) {
        if (updateBeforeAssignment == null) {
            return null;
        }

        return updateBeforeAssignment.apply(playerLocations, servers, currentCentroid, index);
    }

    public Centroid updateAfterAssignment(List<DistancePlayerLocation> playerLocations, Collection<UpdatePacket> servers, Centroid currentCentroid, int index) {
        if (updateAfterAssignment == null) {
            return null;
        }

        return updateAfterAssignment.apply(playerLocations, servers, currentCentroid, index);
    }
}

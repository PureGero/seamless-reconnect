package me.puregero.seamlessreconnect.kmeans;

import com.github.puregero.multilib.MultiLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class DebugCommand extends Command {
    private final ServerAllocationSystem system;

    public DebugCommand(ServerAllocationSystem system) {
        super("srvisualisation");
        setPermission("seamlessreconnect.debug");

        this.system = system;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("seamlessreconnect.debug")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        List<ServerPlayerLocation> players = new ArrayList<>();
        List<UpdatePacket> servers = new ArrayList<>();

        for (UpdatePacket server : system.servers.values()) {
            for (PlayerLocation player : server.playerLocations()) {
                players.add(new ServerPlayerLocation(player, server.serverName()));
            }
            servers.add(server);
        }

        MultiLib.getLocalOnlinePlayers().forEach(player -> players.add(new ServerPlayerLocation(new PlayerLocation(player), MultiLib.getLocalServerName())));
        servers.add(new UpdatePacket(MultiLib.getLocalServerName(), new PlayerLocation[0], system.centerX, system.centerZ));

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (PlayerLocation player : players) {
            if (player.x() < minX) minX = player.x();
            if (player.x() > maxX) maxX = player.x();
            if (player.z() < minZ) minZ = player.z();
            if (player.z() > maxZ) maxZ = player.z();
        }

        if (minX >= maxX || minZ >= maxZ) {
            sender.sendMessage(Component.text("Not enough players online.").color(NamedTextColor.RED));
            return false;
        }

        double ratio = (maxX - minX) / (double) (maxZ - minZ);
        int size = 1000;

        BufferedImage bufferedImage = new BufferedImage(size + 100, (int) (size / ratio) + 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bufferedImage.createGraphics();

        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());

        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Arial", Font.PLAIN, 20));
        graphics.drawString(minX + "," + minZ, 10, 30);
        graphics.drawString(maxX + "," + maxZ, bufferedImage.getWidth() - 10 - graphics.getFontMetrics().stringWidth(maxX + "," + maxZ), bufferedImage.getHeight() - 10);
        graphics.drawRect(50, 50, bufferedImage.getWidth() - 100, bufferedImage.getHeight() - 100);

        graphics.translate(50, 50);
        graphics.scale((double) (bufferedImage.getWidth() - 100) / (maxX - minX), (double) (bufferedImage.getHeight() - 100) / (maxZ - minZ));
        graphics.translate(-minX, -minZ);

        for (ServerPlayerLocation player : players) {
            double closestDistance = Double.MAX_VALUE;
            UpdatePacket closestServer = null;

            for (UpdatePacket server : servers) {
                double distance = Math.pow(player.x() - server.centerX(), 2) + Math.pow(player.z() - server.centerZ(), 2);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestServer = server;
                }
            }

            graphics.setColor(getServerColor(player.server()));
            graphics.fillOval(player.x() - 10, player.z() - 10, 20, 20);

            assert closestServer != null;
            graphics.setColor(getServerColor(closestServer.serverName()));
            graphics.fillOval(player.x() - 7, player.z() - 7, 14, 14);
        }

        graphics.setFont(new Font("Arial", Font.PLAIN, 50));
        graphics.setStroke(new BasicStroke(10));
        for (UpdatePacket server : servers) {
            graphics.setColor(getServerColor(server.serverName()));
            graphics.drawLine(server.centerX() - 50, server.centerZ() - 50, server.centerX() + 50, server.centerZ() + 50);
            graphics.drawLine(server.centerX() - 50, server.centerZ() + 50, server.centerX() + 50, server.centerZ() - 50);
            graphics.drawString(server.serverName(), server.centerX() - graphics.getFontMetrics().stringWidth(server.serverName()) / 2, server.centerZ() - 60);
        }

        CompletableFuture.runAsync(() -> uploadImage(bufferedImage, sender));

        sender.sendMessage(Component.text("Uploading...").color(NamedTextColor.YELLOW));
        return true;
    }

    private void uploadImage(BufferedImage bufferedImage, CommandSender sender) {
        // Send a http request to https://freeimage.host/api/1/upload with the image as a multipart/form-data request as FILES["source"]
        // The response will be a JSON object, and we will send the key "image.url_viewer" to the player

        // Replace 'your-api-key' with the actual API key if required
        String apiKey = "6d207e02198a847aa98d0a2a901485a5";
        String apiUrl = "https://freeimage.host/api/1/upload?key=" + apiKey;

        // Replace 'your-byte-array' with the actual byte array you want to upload
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try {
            ImageIO.write(bufferedImage, "png", buffer);

            HttpClient client = HttpClient.newHttpClient();

            // Prepare the request body
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "multipart/form-data; boundary=boundary")
                    .POST(ofMimeMultipartData("source", "srvisualisation.png", "application/octet-stream", buffer.toByteArray()))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Print the response
            String body = response.body();
            System.out.println("Response Code: " + response.statusCode());
            System.out.println("Response Body: " + body);

            JSONObject jsonObject = (JSONObject) new JSONParser().parse(body);

            String url = (String) ((JSONObject) jsonObject.get("image")).get("url_viewer");
            sender.sendMessage(Component.text(url).hoverEvent(HoverEvent.showText(Component.text(url))).clickEvent(ClickEvent.openUrl(url)).color(NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error uploading image: " + e.getClass().getSimpleName() + ": " + e.getMessage()).color(NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    // Utility method to create a multipart/form-data request body
    private static HttpRequest.BodyPublisher ofMimeMultipartData(String fieldName, String fileName, String mimeType, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 1024);

        buffer.put(("--boundary\r\n" +
                "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));

        buffer.put(data);

        buffer.put("\r\n--boundary--\r\n".getBytes(StandardCharsets.UTF_8));

        return HttpRequest.BodyPublishers.ofByteArray(buffer.array());
    }

    private Color getServerColor(String serverName) {
        Random random = new Random(serverName.hashCode());
        random.nextBytes(new byte[16]);
        return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    private static class ServerPlayerLocation extends PlayerLocation {
        private final String server;

        public ServerPlayerLocation(PlayerLocation location, String server) {
            super(location.x(), location.z());
            this.server = server;
        }

        public String server() {
            return server;
        }
    }
}

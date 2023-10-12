package me.puregero.seamlessreconnect.kmeans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class SetCentroidMethod extends Command {
    private final ServerAllocationSystem system;

    public SetCentroidMethod(ServerAllocationSystem system) {
        super("srsetcentroidmethod");
        setPermission("seamlessreconnect.srsetcentroidmethod");

        this.system = system;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("seamlessreconnect.srsetcentroidmethod")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /srsetcentroidmethod <method>").color(NamedTextColor.RED));
            return false;
        }

        CentroidMethod method;

        try {
            system.setCentroidMethod((method = CentroidMethod.valueOf(args[0].toUpperCase(Locale.ROOT))).name());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid method: " + args[0]).color(NamedTextColor.RED));
            return false;
        }

        sender.sendMessage(Component.text("Set centroid method to " + method.name()).color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        List<String> results = new ArrayList<>();

        if (args.length == 1) {
            for (CentroidMethod method : CentroidMethod.values()) {
                if (method.name().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    results.add(method.name().toLowerCase(Locale.ROOT));
                }
            }
        }

        return results;
    }
}

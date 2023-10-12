package me.puregero.seamlessreconnect.kmeans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SetUpdateRate extends Command {
    private final ServerAllocationSystem system;

    public SetUpdateRate(ServerAllocationSystem system) {
        super("srsetupdaterate");
        setPermission("seamlessreconnect.srsetupdaterate");

        this.system = system;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("seamlessreconnect.srsetupdaterate")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /srsetupdaterate <rate>").color(NamedTextColor.RED));
            return false;
        }

        try {
            system.setUpdateRate(Integer.parseInt(args[0]) * 20);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[0]).color(NamedTextColor.RED));
            return false;
        }

        sender.sendMessage(Component.text("Set update rate to " + args[0] + " seconds").color(NamedTextColor.GREEN));
        return true;
    }
}
